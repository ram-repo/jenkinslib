
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.cloudbees.plugins.credentials.CredentialsProvider
import hudson.util.Secret
import hudson.plugins.git.*
import hudson.model.BuildAuthorizationToken
import org.apache.commons.lang.RandomStringUtils
import hudson.tasks.Mailer
import com.cloudbees.hudson.plugins.folder.Folder
import hudson.scm.SCM
import hudson.model.ListView
import hudson.views.ListViewColumn
import hudson.triggers.SCMTrigger
import hudson.*
import hudson.security.*
import java.util.*
import com.michelin.cio.hudson.plugins.rolestrategy.*
import java.lang.reflect.*
import com.amazonaws.services.ecr.AmazonECRClient
import com.amazonaws.services.ecr.model.CreateRepositoryRequest

def cloneSrcRepo(String srcGit) {
    // Get GIT Creds and URL, need to replace everything from /scm/.* and http
    scmCredsID = scm.getUserRemoteConfigs()[0].getCredentialsId()
    scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
    echo "scmUrl"
    gitServerHost = scmUrl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("/scm/.*", "")
    StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById("${scmCredsID}",
            StandardUsernamePasswordCredentials.class, currentBuild.rawBuild)
     echo "gitServerHost"
    def gitUser = creds.getUsername()
    def gitPass = Secret.toString(creds.getPassword())

    def gitUserUri=gitUser.replace("@", "%40")
    dir ("$workspace") {
        sh """
            rm -rf "${srcGit}"
            git clone https://${gitUserUri}:${gitPass}@${gitServerHost}/${srcGit}.git --depth=1 '${srcGit}'
        """

        // For testing a specific branch:
        //sh "git clone https://${gitUserUri}:${gitPass}@${bitbucket_server_host}/scm/${srcGit}.git -b feature/VET-477 --depth=1 ${srcGit}"
    }

}

def createNewRepo(String projectName, String destGit) {
    // Get GIT Creds and URL, need to replace everything from /scm/.* and http
    //Revisit later when SSL is enabled
    scmCredsID = scm.getUserRemoteConfigs()[0].getCredentialsId()
    scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
    gitServerHost = scmUrl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("/scm/.*", "")
    StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById("${scmCredsID}",
            StandardUsernamePasswordCredentials.class, currentBuild.rawBuild)
    def gitUser = creds.getUsername()
    def gitPass = Secret.toString(creds.getPassword())
    def outputCreateNewRepo = shWithOutput "curl -u ${gitUser}:${gitPass} -X POST -H \"Accept: application/json\"  -H \"Content-Type: application/json\" \"https://${gitServerHost}/${projectName}/repos/\" -d '{\"name\": \"${destGit.split("/").last()}\"}'"
    assert !outputCreateNewRepo.contains("This repository URL is already taken by ") : "Repository $destGit already exists. Please use another repo name or check the environment name being created"
}

def syncSrcToDestRepo(String srcGit, String destGit) {
    // Get GIT Creds and URL, need to replace everything from /scm/.* and http
    scmCredsID = scm.getUserRemoteConfigs()[0].getCredentialsId()
    scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
    gitServerHost = scmUrl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("/scm/.*", "")
    StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById("${scmCredsID}",
            StandardUsernamePasswordCredentials.class, currentBuild.rawBuild)
    def gitUser = creds.getUsername()
    def gitPass = Secret.toString(creds.getPassword())

    def gitUserUri=gitUser.replace("@", "%40")
    println srcGit
    dir ("${workspace}") {
        sh """
            set -e
            cd "${srcGit}"
            ls -al
            rm -rf .git
            git init
            git config user.name "BRE-Jenkins"
            git config user.email bre-jenkins@slalom.com
            git add -A .
            git commit -m "Initial check-in from template"
            git push https://${gitUserUri}:${gitPass}@${gitServerHost}/${destGit}.git master
        """
    }

}

def createNewJenkinsView(String projectName) {
    jobDsl additionalParameters: [
        projectName: projectName
    ], scriptText: '''
        // Create the view
        listView("${projectName}") {
            jobs {
                regex("^${projectName} Projects/.*\\\$")
            }
            columns {
                status()
                weather()
                name()
                lastSuccess()
                lastFailure()
                lastDuration()
            }
            recurse(true)
        }
    '''
}

def createNewJenkinsFolder(String projectsFolder, String projectName) {
    jobDsl additionalParameters: [
        projectsFolder: projectsFolder,
        projectName: projectName
    ], scriptText: '''
        // Get/Create the folder
        folder(projectsFolder) {
            description("Folder for project ${projectName}")
        }
    '''
}

def createNewJenkinsJob(String projectsFolder, String projectName, String destProject, String destGit) {
    // Get GIT Creds and URL, need to replace everything from /scm/.* and http
    scmCredsID = scm.getUserRemoteConfigs()[0].getCredentialsId()
    scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
    gitServerHost = scmUrl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("/scm/.*", "")
    StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById("${scmCredsID}",
            StandardUsernamePasswordCredentials.class, currentBuild.rawBuild)
    def gitUser = creds.getUsername()

    jobDsl additionalParameters: [
        projectsFolder: projectsFolder,
        projectName: projectName,
        destProject: destProject,
        destGit: destGit,
        gitUserUri: gitUser.replace("@", "%40"),
        gitServerHost: gitServerHost,
        scmCredsID: scmCredsID
    ], scriptText: '''
        pipelineJob("${projectsFolder}/${destProject}") {
            definition {
                cpsScm {
                    scm {
                        git {
                            remote {
                                url("https://${gitUserUri}@${gitServerHost}/scm/${destGit}.git")
                                credentials("${scmCredsID}")
                            }
                            extensions {
                                cloneOptions {
                                    depth(1)
                                    shallow(true)
                                }
                            }
                            branch("*/master")
                        }
                    }
                    scriptPath('jenkinsFile.groovy')
                    lightweight(true)
                }
            }
            triggers {
                scm('H/5 * * * *')
            }
        }
    '''
}

def createNewJenkinsJobWithMultiBranch(String projectsFolder, String projectName, String destProject, String destGit, String githubid) {
    // Get GIT Creds and URL, need to replace everything from /scm/.* and https
    scmCredsID = scm.getUserRemoteConfigs()[0].getCredentialsId()
    scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
    gitServerHost = scmUrl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("/scm/.*", "")
    StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById("${scmCredsID}",
            StandardUsernamePasswordCredentials.class, currentBuild.rawBuild)
    def gitUser = creds.getUsername()

    jobDsl additionalParameters: [
        projectsFolder: projectsFolder,
        projectName: projectName,g
        destProject: destProject,
        destGit: destGit,
        gitUserUri: gitUser.replace("@", "%40"),
        gitServerHost: gitServerHost,
        scmCredsID: scmCredsID
    ], scriptText: '''
    multibranchPipelineJob("${projectsFolder}/${destProject}") {
    branchSources {
        github {
            id("${githubid}") // IMPORTANT: use a constant and unique identifier
            scanCredentialsId("${scmCredsID}") // GITHUB_ACCESS
            repoOwner("${projectName}")
            repository("${destProject}")
            includes("master feature/* bugfix/* hotfix/* release/*")
            excludes("donotbuild/*")
        }
    }
  	factory {
        workflowBranchProjectFactory {
            scriptPath("jenkinsFile.groovy")
        }
    }
    triggers {
        periodicFolderTrigger {
            interval("2m")
        }
    }
    orphanedItemStrategy {
        discardOldItems {
            numToKeep(10)
        }
    }
}
'''
}
// executes jenkins sh command but returns the shell output
def shWithOutput(String input) {
    def result  = sh (
        script: input,
        returnStdout: true
    ).trim()
    return result
}

return this

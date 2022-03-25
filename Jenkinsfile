@Library('pipline-demo') _

pipeline {
    agent 'master'
    stage('Setup parameters') {
            steps {
                script {
                     parameters {
        string(name: 'projectname', defaultValue: 'bre', description: 'projectname' )
        // choice(name: 'GOAL', choices: ['package', 'clean package', 'install'], description: 'maven goals')
        string (name: 'destGit', defaultValue: 'reponame', description: 'reponame', trim: true)
        string (name: 'projectsFolder', defaultValue: 'dev', description: 'foldername', trim: true)
        string (name: 'destProject', defaultValue: 'destProject', description: 'destProject', trim: true)
        string (name: 'githubid', defaultValue: 'githubid', description: 'githubid', trim: true)
        string (name: 'destGit', defaultValue: 'destGit', description: 'destGit', trim: true)
        // booleanParam name: 'test', description: 'true'
        }
     }
  }             
    stages{
        stage('create multibranch'){
            steps{
                scripts{
                    repobuilder.createNewRepo("${params.projectName}", "${params.destGit}")
                    repobuilder.createNewJenkinsView("${params.projectName}")
                    repobuilder.createNewJenkinsFolder("${params.projectsFolder}", "${params.projectName}")
                    repobuilder.createNewJenkinsJob("${params.projectsFolder}", "${params.projectName}", "${params.destProject}", "${params.destGit}")
                    repobuilder.createNewJenkinsJobWithMultiBranch("${params.projectsFolder}", "${params.projectName}", "${params.destProject}", "${params.destGit}", "${params.githubid}")
                }
            }
        }
    }
}

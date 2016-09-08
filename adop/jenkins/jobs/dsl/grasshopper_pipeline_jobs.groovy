// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
// **The git repo variables will be changed to the users' git repositories manually in the Jenkins jobs**
def grasshopperAppgitRepo = "grasshoper-greenfield"
def grasshopperAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + grasshopperAppgitRepo
def regressionTestGitRepo = "YOUR_REGRESSION_TEST_REPO"
def regressionTestGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + regressionTestGitRepo

// Jobs
def buildAppJob = freeStyleJob(projectFolderName + "/grasshopper_Application_Build")
def unitTestJob = freeStyleJob(projectFolderName + "/grasshopper_Application_Unit_Tests")
def codeAnalysisJob = freeStyleJob(projectFolderName + "/grasshopper_Application_Code_Analysis")
def deployJob = freeStyleJob(projectFolderName + "/grasshopper_Application_Deploy")
def regressionTestJob = freeStyleJob(projectFolderName + "/grasshopper_Application_Regression_Tests")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/grasshopper_Application")

pipelineView.with{
    title('grasshopper Application Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/grasshopper_Application_Build")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

// All jobs are tied to build on the Jenkins slave
// The functional build steps for each job have been left empty
// A default set of wrappers have been used for each job
// New jobs can be introduced into the pipeline as required

buildAppJob.with{
	description("grasshopper application build job.")
	scm{
		git{
			remote{
				url(grasshopperAppGitUrl)
				credentials("adop-jenkins-master")
			}
			branch("*/Development")
		}
	}
	environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
    }
	label("docker")
	wrappers {
		preBuildCleanup()
		injectPasswords()
		maskPasswords()
		sshAgent("adop-jenkins-master")
	}
	triggers{
		gerrit{
		  events{
			refUpdated()
		  }
		  configure { gerritxml ->
			gerritxml / 'gerritProjects' {
			  'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject' {
				compareType("PLAIN")
				pattern(projectFolderName + "/" + grasshopperAppgitRepo)
				'branches' {
				  'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch' {
					compareType("PLAIN")
					pattern("master")
				  }
				}
			  }
			}
			gerritxml / serverName("ADOP Gerrit")
		  }
		}
	}
	steps {
		shell('''
		    |docker build -t guest_interface guest_interface 
		'''.stripMargin())
	}
	publishers{
		downstreamParameterized{
		  trigger(projectFolderName + "/grasshopper_Application_Unit_Tests"){
			condition("UNSTABLE_OR_BETTER")
			parameters{
			  predefinedProp("B",'${BUILD_NUMBER}')
			  predefinedProp("PARENT_BUILD", '${JOB_NAME}')
			}
		  }
		}
	}
}

unitTestJob.with{
  description("This job runs unit tests on our grasshopper application.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","grasshopper_Application_Build","Parent build name")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
  }
  steps {
    shell('''## YOUR UNIT TESTING STEPS GO HERE'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/grasshopper_Application_Code_Analysis"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD",'${PARENT_BUILD}')
        }
      }
    }
  }
}

codeAnalysisJob.with{
  description("This job runs code quality analysis for our grasshopper application using SonarQube.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","grasshopper_Application_Build","Parent build name")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("docker")
  steps {
    shell('''## YOUR CODE ANALYSIS STEPS GO HERE'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/grasshopper_Application_Deploy"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

deployJob.with{
  description("This job deploys the grasshopper application to the CI environment")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","grasshopper_Application_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
    shell('''## YOUR DEPLOY STEPS GO HERE'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/grasshopper_Application_Regression_Tests"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
          predefinedProp("ENVIRONMENT_NAME", '${ENVIRONMENT_NAME}')
        }
      }
    }
  }
}

regressionTestJob.with{
  description("This job runs regression tests on the deployed grasshopper application")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","grasshopper_Application_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  scm{
    git{
      remote{
        url(regressionTestGitUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
    shell('''## YOUR REGRESSION TESTING STEPS GO HERE'''.stripMargin())
  }
}

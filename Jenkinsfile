// 自定义构建参数，覆盖cicd/services.yml下的build值。若无特殊需求，则保持原样。若node_build_mode为docker，则表示使用容器构建前端
def SERVICE_BUILD_OVERRIDES = []

// 示例
// def SERVICE_BUILD_OVERRIDES = [
//     'ams': [
//         gradle_task: 'bootJar'
//     ],
//     'ams-web': [
//         nodejs_tool: 'NodeJS18',
//         node_install_command: 'yarn',
//         node_build_command: 'yarn build'
//     ]
//     'other-web': [
//         node_build_mode: 'docker',
//         node_install_command: 'npm install',
//         node_docker_image: 'rddhub.changhong.com/library/node:22.15-slim'
//     ]
// ]

// 字符串转换为列表
def listOf(def raw) {
    return (raw ?: '').toString().split(/[\n,]+/).collect { it.trim() }.findAll { it }
}
// 取值，否则返回默认值
def valueOf(def raw, def fallback = '') {
    def text = raw == null ? '' : raw.toString().trim()
    return text ? text : fallback
}
// 获取服务配置，如果没配置则使用默认值
def serviceValue(def svc, String serviceName, String key) {
    def defaults = [
        image: serviceName,
        deployment: serviceName,
        container: serviceName,
        compose_service: serviceName
    ]
    def value = svc instanceof Map ? svc[key] : null

    return valueOf(value, defaults[key])
}
// 获取构建配置，如果没配置则使用默认值
def buildValue(def overrides, def config, def svc, String serviceName, String type, String key) {
    def jenkinsBuild = overrides instanceof Map && overrides[serviceName] instanceof Map ? overrides[serviceName] : [:]
    def serviceBuild = svc instanceof Map && svc.build instanceof Map ? svc.build : [:]
    def typeBuild = config.defaults instanceof Map && config.defaults[type] instanceof Map && config.defaults[type].build instanceof Map
        ? config.defaults[type].build
        : [:]

    return valueOf(jenkinsBuild[key], valueOf(serviceBuild[key], typeBuild[key]))
}
// shell转义，避免空格，单引号，特殊字符
def shellQuote(String text) {
    return "'" + text.replace("'", "'\"'\"'") + "'"
}
// 清洗镜像仓库前缀 https或http
def repositoryPrefix(def raw) {
    return valueOf(raw).replaceFirst('^https?://', '').replaceAll('/+$', '')
}
// 获取镜像仓库地址对应的域名
def imageRegistry(String imageRepositoryPrefix) {
    return repositoryPrefix(imageRepositoryPrefix).split('/')[0]
}
// 获取镜像名称
def imageName(String imageRepositoryPrefix, String image) {
    def prefixText = repositoryPrefix(imageRepositoryPrefix)
    def imageText = image.trim().replaceAll('^/+', '')

    return "${prefixText}/${imageText}"
}
// 解析远程主机信息
def remoteHost(String hostSpec) {
    def matcher = hostSpec.trim() =~ /^(.+):(\d+)$/
    return matcher.matches()
        ? [host: matcher[0][1], port: matcher[0][2].toInteger()]
        : [host: hostSpec.trim(), port: 22]
}
// 脚本式 Jenkinsfile
node {

    // 全局变量
    def config = null
    def services = []
    def newImageTag = ''
    def gitCommit = ''

    // Jenkins上配置的GitLab凭据，根据实际情况修改
    def GIT_CREDENTIALS_ID = 'rddgit.changhong.com'
    // 镜像TAG，根据实际情况修改
    def IMAGE_TAG = 'dev'
    // Jenkins上配置的镜像仓库凭据，根据实际情况修改
    def IMAGE_CREDENTIALS_ID = 'rddhub.changhong.com'
    // 镜像仓库前缀，例如 rddhub.changhong.com/sei/eadp，根据实际情况修改
    def IMAGE_REPOSITORY_PREFIX = 'rddhub.changhong.com/sei/eadp'
    // Jenkins上配置的应用服务器SSH凭据，根据实际情况修改
    def SSH_CREDENTIALS_ID = 'e122dbdd-35de-4ef3-7e14-bb6d75a93843'

    stage('项目初始化') {
        properties([
            buildDiscarder(logRotator(numToKeepStr: '5')),
            disableConcurrentBuilds(),
            parameters([
                string(name: 'PROJECT_GIT_PATH', defaultValue: 'http://rddgit.changhong.com/project/sei/online-code.git', description: 'GitLab 代码仓库地址'),
                string(name: 'BRANCH', defaultValue: 'dev', description: '代码分支或者 TAG'),
                booleanParam(name: 'CLEAR_WORKSPACE', defaultValue: false, description: '构建前清理 Jenkins 工作空间，会触发所有服务重新构建，若要构建某一个服务，在 BUILD_SERVICES 填入具体的服务名，若有多个，以英文逗号隔开！'),
                booleanParam(name: 'FORCE_BUILD_ALL', defaultValue: false, description: '忽略变更检测，构建全部服务'),
                booleanParam(name: 'CLEAR_SUCCESS_MARKS', defaultValue: false, description: '清理当前提交的成功构建记录，重新构建，若要构建某一个服务，在 BUILD_SERVICES 填入具体的服务名，若有多个，以英文逗号隔开！'),
                booleanParam(name: 'SKIP_SUCCESS_SERVICES', defaultValue: true, description: '重新构建时跳过上次已成功构建的服务'),
                string(name: 'BUILD_SERVICES', defaultValue: '', description: '手动指定服务，逗号或换行分隔；为空时按 Git 变更自动识别（手动排障时才指定）'),
                string(name: 'SSH_REMOTE_HOSTS', defaultValue: '10.199.11.1', description: 'docker 部署目标机器，逗号或换行分隔，如：10.199.11.1,10.199.11.2:2222；不同端口可写 10.199.11.2:2222'),
                string(name: 'SERVICE_CONFIG_FILE', defaultValue: 'cicd/services.yml', description: 'monorepo 服务清单, 目录和文件名称是固定值'),
            ]),
            // 每2分钟检查一次git变更 
            pipelineTriggers([pollSCM('H/2 * * * * ')])
        ])

        // 清理工作空间
        if (params.CLEAR_WORKSPACE) {
            cleanWs()
        }
    }

    stage('获取代码') {
        checkout([
            $class: 'GitSCM',
            branches: [[
                name: "${params.BRANCH}"
            ]],
            userRemoteConfigs: [[
                credentialsId: "${GIT_CREDENTIALS_ID}", 
                url: "${params.PROJECT_GIT_PATH}"
            ]]
        ])

        def shortCommit = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
        gitCommit = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        newImageTag = "stable-${shortCommit}"

        if (params.CLEAR_SUCCESS_MARKS) {
            sh """
                rm -rf .cicd-success/${gitCommit}
            """
            echo "已清理提交 ${gitCommit} 的成功记录"
        }

        currentBuild.description = "构建分支: ${params.BRANCH}, 提交ID: ${shortCommit}, 镜像TAG: ${newImageTag}"
    }

    stage('读取配置并确定服务') {
        config = readYaml(file: params.SERVICE_CONFIG_FILE)
        if (!(config instanceof Map) || !(config.services instanceof Map) || config.services.isEmpty()) {
            error("${params.SERVICE_CONFIG_FILE} 中没有可用的 services 配置")
        }
        config.services.each { name, svc ->
            def type = serviceValue(svc, name.toString(), 'type')
            if (!serviceValue(svc, name.toString(), 'path')) {
                error("服务 ${name} 缺少 path 配置")
            }
            if (!['java', 'nodejs'].contains(type)) {
                error("服务 ${name} 的 type 必须是 java 或 nodejs")
            }
        }

        def allServices = config.services.keySet().collect { it.toString() }
        def manualServices = listOf(params.BUILD_SERVICES)

        if (params.FORCE_BUILD_ALL) {
            services = allServices
        } else if (manualServices) {
            services = manualServices
        } else {
            def changedFilesText = sh(
                script: '''
                    if [ -n "$GIT_PREVIOUS_SUCCESSFUL_COMMIT" ] && git cat-file -e "$GIT_PREVIOUS_SUCCESSFUL_COMMIT^{commit}" 2>/dev/null; then
                        git diff --name-only "$GIT_PREVIOUS_SUCCESSFUL_COMMIT" HEAD
                    elif git rev-parse HEAD~1 >/dev/null 2>&1; then
                        git diff --name-only HEAD~1 HEAD
                    else
                        git ls-files
                    fi
                ''',
                returnStdout: true
            ).trim()
            def changedFiles = changedFilesText ? changedFilesText.split('\n').collect { it.trim() }.findAll { it } : []

            if (changedFiles.contains(params.SERVICE_CONFIG_FILE)) {
                services = allServices
            } else {
                config.services.each { name, svc ->
                    def path = serviceValue(svc, name.toString(), 'path')
                    def prefix = path.endsWith('/') ? path : "${path}/"
                    if (changedFiles.any { it.startsWith(prefix) }) {
                        services.add(name.toString())
                    }
                }
            }
            echo "变更文件: ${changedFiles}"
        }

        services = services.unique()
        def unknown = services.findAll { !config.services.containsKey(it) }
        if (unknown) {
            error("services.yml 中不存在服务: ${unknown.join(', ')}")
        }

        echo "本次构建服务: ${services ? services.join(', ') : '无'}"
    }

    stage('构建并推送镜像') {
        if (!services) {
            echo '没有服务需要构建，跳过'
            return
        }

        services.collate(services.size()).eachWithIndex { batch, batchIndex ->
            echo "并发构建批次 ${batchIndex + 1}: ${batch.join(', ')}"

            def jobs = [:]
            batch.each { item ->
                def serviceName = item.toString()
                jobs[serviceName] = {
                    
                    // 构建前检查是否已经构建成功过
                    def successMark = ".cicd-success/${gitCommit}/${serviceName}"
                    if (params.SKIP_SUCCESS_SERVICES && fileExists(successMark)) {
                        echo "服务 ${serviceName} 在当前提交 ${gitCommit} 已构建成功，本次跳过"
                        return
                    }

                    def svc = config.services[serviceName]
                    def type = serviceValue(svc, serviceName, 'type')
                    def path = serviceValue(svc, serviceName, 'path')
                    def repoImage = imageName(IMAGE_REPOSITORY_PREFIX, serviceValue(svc, serviceName, 'image'))
                    def tagArgs = [newImageTag, valueOf(IMAGE_TAG)].findAll { it }.unique().collect { "-t ${repoImage}:${it}" }.join(' ')

                    echo "构建 ${serviceName}: ${repoImage}"

                    dir(path) {
                        timeout(time: 30, unit: 'MINUTES') {
                            if (type == 'java') {
                                // jdk21和gradle8.7 为Jenkins上设置的工具名称，根据实际情况修改。
                                def javaHome = tool name: 'jdk21', type: 'jdk'
                                def gradleHome = tool name: 'gradle8.7', type: 'gradle'
                                def gradleTask = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'gradle_task')

                                sh "export JAVA_HOME=${javaHome} && ${gradleHome}/bin/gradle --no-daemon ${gradleTask} --refresh-dependencies"

                            } else if (type == 'nodejs') {
                                def nodeMode = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'node_build_mode')
                                def installCommand = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'node_install_command')
                                def buildCommand = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'node_build_command')
                                def nodeOptions = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'node_options')

                                //  编译构建前清理前端生成的dist目录
                                sh '''
                                    if [ -d dist ]; then
                                        rm -rf dist
                                    fi
                                '''
                                if (nodeMode == 'docker') {
                                    def nodeDockerImage = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'node_docker_image')
                                    def currentDir = pwd()
                                    sh """
                                        docker run \
                                          -v ${shellQuote(currentDir)}:/app \
                                          -e NODE_OPTIONS=${shellQuote(nodeOptions)} \
                                          --rm ${nodeDockerImage} \
                                          sh -c ${shellQuote("cd /app && ${installCommand} && ${buildCommand}")}
                                    """
                                } else {
                                    nodejs(buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'nodejs_tool')) {
                                        sh """
                                            export NODE_OPTIONS=${shellQuote(nodeOptions)}
                                            ${installCommand}
                                            ${buildCommand}
                                        """
                                    }
                                }
                            } else {
                                error("未知服务类型: ${type}")
                            }
                        }

                        timeout(time: 20, unit: 'MINUTES') {
                            def dockerContext = buildValue(SERVICE_BUILD_OVERRIDES, config, svc, serviceName, type, 'docker_context')
                            
                            if (dockerContext.contains("..") || dockerContext.startsWith("/")) {
                                error "检测到非法的路径穿透攻击！docker_context的值只能是只能是工作空间内的相对路径，不能包含 .. 或使用绝对路径"
                            }

                            if (type == 'nodejs' && dockerContext != '.') {
                                sh """
                                    if [ -d ${shellQuote(dockerContext)}/dist ]; then
                                        rm -rf ${shellQuote(dockerContext)}/dist
                                    fi
                                    cp -rf dist ${shellQuote(dockerContext)}/dist
                                """
                            }
                            dir(dockerContext) {
                                // 默认构建aarch64和x86_64架构的镜像，两个镜像标签
                                sh """
                                    docker buildx build \
                                      --label org.opencontainers.image.revision=${gitCommit} \
                                      --label org.opencontainers.image.vendor='Hongxin' \
                                      --platform linux/amd64,linux/arm64 \
                                      ${tagArgs} \
                                      --push .
                                """
                                //构建单一架构镜像, 两个标签
                                // sh """
                                //     docker build --label org.opencontainers.image.revision=${gitCommit} --label org.opencontainers.image.vendor='Hongxin' ${tagArgs} .
                                //     docker push ${repoImage}:${IMAGE_TAG}
                                //     docker push ${repoImage}:${newImageTag}
                                //     docker rmi ${repoImage}:${IMAGE_TAG}
                                //     docker rmi ${repoImage}:${newImageTag}
                                // """

                                //构建单一架构镜像, 一个镜像标签
                                // sh """
                                //     docker build \
                                //         --label org.opencontainers.image.revision=${gitCommit} \
                                //         --label org.opencontainers.image.vendor='Hongxin' \
                                //         -t ${repoImage}:${IMAGE_TAG} .
                                //     docker push ${repoImage}:${IMAGE_TAG}
                                //     docker rmi ${repoImage}:${IMAGE_TAG}
                                // """
                                
                            }
                        }
                    }

                    // 构建成功标记
                    sh "mkdir -p .cicd-success/${gitCommit}"
                    writeFile(
                        file: ".cicd-success/${gitCommit}/${serviceName}",
                        text: newImageTag
                    )

                    echo "服务 ${serviceName} 构建成功，已写入成功标记"
                }
            }

            parallel jobs
        }
    }

    // stage('部署服务') {
    //     if (!services) {
    //         echo '没有服务需要部署，跳过'
    //         return
    //     }

    //     def hosts = listOf(params.SSH_REMOTE_HOSTS)

    //     withCredentials([usernamePassword(credentialsId: "${SSH_CREDENTIALS_ID}", passwordVariable: 'sshPassword', usernameVariable: 'sshUser')]) {
    //         hosts.each { hostSpec ->
    //             def hostInfo = remoteHost(hostSpec)
    //             def remote = [
    //                 name: "${hostInfo.host}:${hostInfo.port}",
    //                 host: hostInfo.host,
    //                 port: hostInfo.port,
    //                 allowAnyHosts: true,
    //                 user: sshUser,
    //                 password: sshPassword
    //             ]

    //             services.each { serviceName ->
    //                 def svc = config.services[serviceName]
    //                 def repoImage = imageName(IMAGE_REPOSITORY_PREFIX, serviceValue(svc, serviceName, 'image'))
    //                 def composeService = serviceValue(svc, serviceName, 'compose_service')
    //                 // 以下的sudo和/opt/app根据实际情况调整
    //                 sshCommand remote: remote, command: "sudo docker pull ${repoImage}:${IMAGE_TAG}"
    //                 sshCommand remote: remote, command: "cd /opt/app; sudo docker-compose up -d ${shellQuote(composeService)}"
    //             }
    //         }
    //     }
    // }
}

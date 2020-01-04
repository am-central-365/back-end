FROM    gradle:6.0.1-jdk8
USER    gradle
WORKDIR /home/gradle/project
CMD     ["gradle", "testITTask"]

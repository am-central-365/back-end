FROM    openjdk:8
RUN     useradd -ms /bin/bash app && mkdir /home/app/amcentral365
WORKDIR /home/app/amcentral365
USER    app
CMD     ["java", "-jar", "build/libs/am-central365-service-0.0.1.jar", \
            "--conn", "jdbc:mariadb://db:3306/amcentral365?useSSL=false", \
            "--merge-roles", \
            "--merge-assets" \
        ]

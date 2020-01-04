FROM ubuntu:16.04

ENV NOTVISIBLE "in users profile"
RUN apt-get update && apt-get install -y openssh-server \
    ; mkdir /var/run/sshd \
    ; echo 'root:r' | chpasswd \
    ; sed -i 's/PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config \
    # SSH login fix. Otherwise user is kicked off after login \
    ; sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd \
    ; echo "export VISIBLE=now" >> /etc/profile

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]

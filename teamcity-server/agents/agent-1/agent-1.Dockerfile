ARG TEAMCITY_VERSION
FROM jetbrains/teamcity-agent:${TEAMCITY_VERSION}-linux-sudo
RUN sudo apt-get update && \
    sudo apt-get install -y openjdk-11-jdk

CMD ["/run-agent.sh"]

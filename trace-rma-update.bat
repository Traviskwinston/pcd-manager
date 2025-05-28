@echo off
echo Starting server with enhanced RMA logging...
set JAVA_OPTS=-Dspring.profiles.active=dev -Dlogging.level.com.pcd.manager.controller.RmaController=DEBUG -Dlogging.level.com.pcd.manager.service.RmaService=DEBUG
mvn spring-boot:run -Dspring-boot.run.jvmArguments="%JAVA_OPTS%" 
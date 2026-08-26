FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/roundtrips-paper-problem-instances-0.0.1-SNAPSHOT.jar app.jar
COPY data/survey/ data/survey/

# COPY data/ground_truth/amal_measurement/out/ProductionRun_1000/partitions_merged data/ground_truth/amal_measurement/out/ProductionRun_1000/partitions__merged
# COPY data/ground_truth/amal_validation/out/ProductionRun_1000/partitions_merged data/ground_truth/amal_validation/out/ProductionRun_1000/partitions__merged

ENV RUN_NAME=docker

ENTRYPOINT ["java", "-cp", "app.jar", "disaggregate.Runner"]
CMD ["--scenarioDir", "data/survey", "--name", "amal", "--outputDir", "output/"]
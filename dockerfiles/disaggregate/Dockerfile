FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY target/roundtrips-paper-problem-instances-0.0.1-SNAPSHOT.jar app.jar
COPY data/ data/

# COPY data/ground_truth/amal_measurement/out/ProductionRun_1000/partitions_merged data/ground_truth/amal_measurement/out/ProductionRun_1000/partitions__merged
# COPY data/ground_truth/amal_validation/out/ProductionRun_1000/partitions_merged data/ground_truth/amal_validation/out/ProductionRun_1000/partitions__merged

ENV RUN_NAME=docker

ENTRYPOINT ["java", "-cp", "app.jar"]
CMD ["disaggregate.Runner"]
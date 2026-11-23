# Build the war and the runtime libs on the host first (works offline from ~/.m2):
#   ./scripts/build-image.sh        (= mvn package + copy hibernate/mysql jars + docker compose build)
#   or in IntelliJ: run configuration "Docker image"
# No BuildKit-only syntax here: IntelliJ in a Flatpak may build through the plain Docker API.
FROM tomee:11.0.0-M2-jre25-alpine-microprofile

ENV TZ="Europe/Berlin"

RUN rm -rf /usr/local/tomee/webapps/*
COPY target/kabuproxy/ /usr/local/tomee/webapps/ROOT/
# the war is built for dev (JSF stage Development = detailed error pages); the image must never show those
RUN sed -i 's#<param-value>Development</param-value>#<param-value>Production</param-value>#' /usr/local/tomee/webapps/ROOT/WEB-INF/web.xml \
    && grep -q "<param-value>Production</param-value>" /usr/local/tomee/webapps/ROOT/WEB-INF/web.xml

# Hibernate as JPA provider (instead of OpenJPA) + MySQL driver
COPY target/docker-lib/ /usr/local/tomee/lib/
RUN rm /usr/local/tomee/lib/openjpa-*.jar \
    && echo "jakarta.persistence.provider=org.hibernate.jpa.HibernatePersistenceProvider" >> /usr/local/tomee/conf/system.properties \
    && echo "tomee.jpa.factory.lazy=true" >> /usr/local/tomee/conf/system.properties \
    && echo "tomee.mp.scan=all" >> /usr/local/tomee/conf/system.properties

COPY docker/tomee.xml /usr/local/tomee/conf/tomee.xml
COPY docker/entrypoint.sh /opt/entrypoint.sh
RUN chmod 755 /opt/entrypoint.sh

# build timestamp shown on the admin page (last layer, so any change above refreshes it)
RUN date -u +%Y-%m-%dT%H:%M:%SZ > /usr/local/tomee/webapps/ROOT/WEB-INF/build-time

EXPOSE 8080
ENTRYPOINT ["/opt/entrypoint.sh"]
CMD ["catalina.sh", "run"]

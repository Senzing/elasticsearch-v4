# Senzing with ElasticSearch

## Overview

This code project demonstrates how the Senzing v4 engine may be used with an ElasticSearch indexing engine. ElasticSearch provides enhanced searching capabilities on entity data.

The Senzing data repository contains data records and observations about known entities. It determines which records match/merge to become single resolved entities. These resolved entities can be indexed through the ElasticSearch engine, to provide more searchable data entities.

ElasticSearch stores its indexed entity data in a separate data repository than the Senzing engine does. Thus, ElasticSearch and Senzing must both be managed in order to keep them in sync.

### Preamble

At [Senzing], we strive to create GitHub documentation in a
"don't make me think" style. For the most part, instructions are copy and paste.
Whenever thinking is needed, it's marked with a "thinking" icon :thinking:.
Whenever customization is needed, it's marked with a "pencil" icon :pencil2:.
If the instructions are not clear, please let us know by opening a new
[Documentation issue] describing where we can improve. Now on with the show...

### Legend

1. :thinking: - A "thinker" icon means that a little extra thinking may be required.
   Perhaps there are some choices to be made.
   Perhaps it's an optional step.
1. :pencil2: - A "pencil" icon means that the instructions may need modification before performing.
1. :warning: - A "warning" icon means that something tricky is happening, so pay attention.

### Expectations

- **Space:** This repository and demonstration require X GB free disk space.
- **Time:** Budget 30 minutes to get the demonstration up-and-running, depending on CPU and network speeds.
- **Background knowledge:** This repository assumes a working knowledge of:

  - [Docker]
  - [Elasticsearch]
  - [git]
  - [kibana]

## Prerequisites

1. [Docker]
1. [git]
1. [maven] and [java] 25 or later (only needed to build outside of Docker)

## Demonstration

### Load Data

- 🤔 Data needs to be loaded into a Senzing project to post to elasticsearch, if you don't have any data to load, or don't know how, visit our [quickstart].

### Startup elasticsearch

1. Start an instance of elasticsearch and your favorite elastic search UI, kibana is recommended and will be assumed for the remainder of this demonstration.
   For more options, see [Install Elasticsearch with Docker] and [Install Kibana with Docker].

1. :thinking: Create the docker network unless it already exists (for example, if it was created by `elasticsearch/docker-compose.yaml`).

   ```console
   sudo docker network create senzing-network
   ```

1. Start elasticsearch and kibana. Example:

   ```console
   sudo docker run --detach \
     --name senzing-elasticsearch \
     --network senzing-network \
     --publish 9200:9200 \
     --env discovery.type=single-node \
     --env xpack.security.enabled=false \
     --env ES_JAVA_OPTS="-Xms1g -Xmx1g" \
     docker.elastic.co/elasticsearch/elasticsearch:9.5.4

   sudo docker run --detach \
     --name senzing-kibana \
     --network senzing-network \
     --publish 5601:5601 \
     --env ELASTICSEARCH_HOSTS=http://senzing-elasticsearch:9200 \
     docker.elastic.co/kibana/kibana:9.5.4
   ```

### Build project

1. :pencil2: Set local environment variables. These variables may be modified, but do not need to be modified. The variables are used throughout the installation procedure.

   ```console
   export GIT_ACCOUNT=senzing
   export GIT_REPOSITORY=elasticsearch-v4
   export GIT_ACCOUNT_DIR=~/${GIT_ACCOUNT}.git
   export GIT_REPOSITORY_DIR="${GIT_ACCOUNT_DIR}/${GIT_REPOSITORY}"
   ```

1. Clone the repository
   ```console
   cd ${GIT_ACCOUNT_DIR}
   git clone https://github.com/Senzing/elasticsearch-v4.git
   cd ${GIT_REPOSITORY_DIR}
   ```
1. :thinking: Make sure the [SENZING_ENGINE_CONFIGURATION_JSON] environment variable is set to the Senzing installation that the data was loaded into earlier

1. :thinking: Set elasticsearch local environment variables. The hostname and port must point towards the exposed port that the elasticsearch instance has. The index name can be anything; conforming to elasticsearch's index syntax.

   ```console
   export ELASTIC_HOSTNAME=senzing-elasticsearch
   export ELASTIC_PORT=9200
   export ELASTIC_INDEX_NAME=senzing-index
   ```

1. Build the docker container.

   ```console
   cd {GIT_REPOSITORY_DIR}
   sudo docker build -t senzing/elasticsearch-v4 .
   ```

1. :thinking: Optional: to build the jar outside of Docker, a Senzing v4 SDK installation is required.
   The Senzing Java SDK is not published to Maven Central, so first install the copy that ships with Senzing into the local Maven repository.
   The version must match `sz-sdk.version` in `elasticsearch/pom.xml`.

   ```console
   cd ${GIT_REPOSITORY_DIR}/elasticsearch
   mvn install:install-file \
     -Dfile=/opt/senzing/er/sdk/java/sz-sdk.jar \
     -DgroupId=com.senzing \
     -DartifactId=sz-sdk \
     -Dversion=4.4.2 \
     -Dpackaging=jar
   mvn clean package
   ```

### Run the indexer

#### Using a local sqlite Senzing database

1. We will mount the sqlite database; make sure the `CONNECTION` string in our config json points to where it is mounted. In this example the `CONNECTION` will need to point towards the `/db` dir. We also need to run the container as part of the network that the ELK-stack is running in. Example:

   ```console
   sudo --preserve-env docker run \
     --interactive \
     --rm \
     --tty \
     -e ELASTIC_HOSTNAME \
     -e ELASTIC_PORT \
     -e ELASTIC_INDEX_NAME \
     -e SENZING_ENGINE_CONFIGURATION_JSON \
     --network=senzing-network \
     --volume ~/senzing/var/sqlite:/db \
     senzing/elasticsearch-v4
   ```

#### Using an external Senzing database

1.  Here we won't need to mount a database, instead we can set our `CONNECTION` string in the config json to where the external database is. Example:

    ```console
    export SENZING_ENGINE_CONFIGURATION_JSON='{
    "PIPELINE": {
        "CONFIGPATH": "/etc/opt/senzing",
        "RESOURCEPATH": "/opt/senzing/er/resources",
        "SUPPORTPATH": "/opt/senzing/data"
       },
    "SQL": {
        "CONNECTION": "postgresql://postgres:postgres@senzing-postgres:5432:senzing"
       }
      }'
    ```

1.  Now we can run the container as part of the network that the ELK-stack is running in so that it can "see" the elasticsearch container. Example:

    ```console
    sudo --preserve-env docker run \
      --interactive \
      --rm \
      --tty \
      -e ELASTIC_HOSTNAME \
      -e ELASTIC_PORT \
      -e ELASTIC_INDEX_NAME \
      -e SENZING_ENGINE_CONFIGURATION_JSON \
      --network=senzing-network \
      senzing/elasticsearch-v4
    ```

### Search data

1. Open up kibana in a web browser, default: [localhost:5601]

2. Navigate to the discover tab

<img width="200" alt="image" src="https://github.com/Senzing/elasticsearch/assets/49598357/b7663a5b-b940-4ca6-b3b6-dc0250a5f3ba">

3. Create Index.

   - Click the "Data view" menu at the top left of the screen, then click "Create a data view".
   - In the `index pattern` box type the name of the index that was created, this was the `ELASTIC_INDEX_NAME` variable set early, and should also appear on the right side of the popup.
   - The `Name` field can be set but is not required.
   - The indexed entities have no timestamp, so for `Timestamp field` select "--- I don't want to use the time filter ---".

4. Press "Save data view to Kibana" at the bottom of the screen. You can now view the created index and do searches. If fuzzy searches are needed click on the menu button to the left of the search bar, choose "Language" and switch the language to lucene. [Here] you can view the lucene syntax and how to do fuzzy searches
   <img width="246" alt="image" src="https://github.com/SamMacy/elasticsearch/assets/49598357/c77b8f8b-6877-4701-9677-511e5aafb81f">

[Docker]: https://docs.docker.com/get-started/get-docker/
[Documentation issue]: https://github.com/Senzing/elasticsearch-v4/issues/new
[Elasticsearch]: https://www.elastic.co/guide/en/elasticsearch/reference/current/install-elasticsearch.html
[git]: https://git-scm.com/
[Here]: https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-query-string-query#query-string-fuzziness
[Install Elasticsearch with Docker]: https://www.elastic.co/docs/deploy-manage/deploy/self-managed/install-elasticsearch-with-docker
[Install Kibana with Docker]: https://www.elastic.co/docs/deploy-manage/deploy/self-managed/install-kibana-with-docker
[java]: https://adoptium.net/
[kibana]: https://www.elastic.co/guide/en/kibana/current/install.html
[localhost:5601]: http://localhost:5601
[maven]: https://maven.apache.org/
[quickstart]: https://www.senzing.com/docs/quickstart/
[SENZING_ENGINE_CONFIGURATION_JSON]: https://www.senzing.com/docs/tutorials/senzing_engine_config/
[Senzing]: https://senzing.com
 

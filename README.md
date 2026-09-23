## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).

### Running order-service locally

`order-service` persists orders to PostgreSQL. Start a local database first:

```
docker compose up -d
```

Then run the service as usual, e.g. `sbt "order-service/run"` or `./scripts/run-services.sh`
to start both services together. `application.conf` defaults match the compose file
(`localhost:5432`, db/user/password `orders`) — no manual schema setup is needed, Flyway
migrations run automatically on startup.

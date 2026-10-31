import java.nio.file.Path;
import org.flywaydb.core.Flyway;

/** Operator-only schema migration, without starting application consumers or scheduled jobs. */
class MigrateResetSchema {
  public static void main(String[] args) {
    Path root=Path.of(args[0]);
    for(String[] pair:new String[][]{{"auth","auth"},{"account","account"},{"order","order_svc"},{"market-data","market_data"},{"audit","audit"},{"simulator","simulator"}}) {
      Flyway.configure().dataSource(System.getenv("RESET_DB_URL"),System.getenv("RESET_DB_USER"),System.getenv("RESET_DB_PASSWORD"))
        .schemas(pair[1]).defaultSchema(pair[1])
        .locations("filesystem:"+root.resolve("services/"+pair[0]+"/src/main/resources/db/migration"))
        .load().migrate();
    }
  }
}

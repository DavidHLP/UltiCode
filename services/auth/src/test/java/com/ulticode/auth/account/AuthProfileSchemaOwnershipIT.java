package com.ulticode.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Real-MySQL proof for the Auth account/profile schema contract migration. */
@Testcontainers
@DisplayName("Auth account/profile schema ownership contract")
class AuthProfileSchemaOwnershipIT {

    private static final List<String> PROFILE_COLUMNS = List.of(
            "name", "avatar", "bio", "company", "github", "location", "twitter",
            "website", "preferred_language");
    private static final List<String> ACCOUNT_COLUMNS = List.of(
            "id", "username", "email", "password", "joined_at", "role", "is_active",
            "is_banned", "banned_until", "banned_reason", "last_login_at", "created_by",
            "updated_by", "is_deleted", "deleted_at", "deleted_by",
            "password_reset_token_hash", "password_reset_expires_at", "authz_version");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_auth_schema_contract_test")
            .withUsername("root")
            .withPassword("root")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath(
                            "V20260729140100__Create_Auth_Schema_Tables.sql").toString()),
                    "/docker-entrypoint-initdb.d/01-auth-schema.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath(
                            "V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql").toString()),
                    "/docker-entrypoint-initdb.d/02-auth-profile-contract.sql");

    private static JdbcTemplate jdbcTemplate;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);

        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(),
                new UnpooledDataSource(
                        MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(), MYSQL.getPassword())));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AuthAccountMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    @DisplayName("contract removes profile columns and retains account/authz columns")
    void contractKeepsAuthAccountShape() {
        Set<String> columns = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'users'",
                String.class));

        assertThat(columns).doesNotContainAnyElementsOf(PROFILE_COLUMNS);
        assertThat(columns).containsAll(ACCOUNT_COLUMNS);
    }

    @Test
    @DisplayName("account mapper remains executable after profile contract")
    void accountMapperUsesOnlyAuthOwnedColumns() {
        AuthAccountEntity account = new AuthAccountEntity();
        account.setId("account-1");
        account.setUsername("account_user");
        account.setEmail("account@example.com");
        account.setPassword("password-hash");
        account.setRole("USER");
        account.setActive(true);
        account.setBanned(false);
        account.setJoinedAt(LocalDateTime.of(2026, 8, 20, 12, 0));
        account.setAuthzVersion(0L);

        try (var session = sqlSessionFactory.openSession(true)) {
            AuthAccountMapper mapper = session.getMapper(AuthAccountMapper.class);
            assertThat(mapper.insert(account)).isEqualTo(1);

            AuthAccountEntity persisted = mapper.findById(account.getId());
            assertThat(persisted).isNotNull();
            assertThat(persisted.getUsername()).isEqualTo(account.getUsername());
            assertThat(persisted.getEmail()).isEqualTo(account.getEmail());
            assertThat(persisted.getAuthzVersion()).isZero();
        }
    }

    private static Path migrationPath(String filename) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations/auth/" + filename);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(filename + " not found from user.dir="
                + System.getProperty("user.dir"));
    }
}

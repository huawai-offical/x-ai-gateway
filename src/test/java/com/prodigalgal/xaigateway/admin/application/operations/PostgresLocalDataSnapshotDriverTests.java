package com.prodigalgal.xaigateway.admin.application.operations;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresLocalDataSnapshotDriverTests {

    @Test
    void shouldReturnUnavailableWhenPgDumpMissing() {
        CommandRunner commandRunner = Mockito.mock(CommandRunner.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test_db")
                .withProperty("spring.datasource.username", "tester")
                .withProperty("spring.datasource.password", "secret");
        Mockito.when(commandRunner.run(List.of("pg_dump", "--version"), null, Map.of()))
                .thenReturn(new CommandRunner.CommandResult(1, "", "missing"));
        PostgresLocalDataSnapshotDriver driver = new PostgresLocalDataSnapshotDriver(environment, commandRunner);

        var result = driver.checkAvailability();

        assertEquals(false, result.available());
    }

    @Test
    void shouldThrowWhenDumpCommandFails() {
        CommandRunner commandRunner = Mockito.mock(CommandRunner.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test_db")
                .withProperty("spring.datasource.username", "tester")
                .withProperty("spring.datasource.password", "secret");
        Mockito.when(commandRunner.run(Mockito.anyList(), Mockito.any(), Mockito.anyMap()))
                .thenReturn(new CommandRunner.CommandResult(1, "", "boom"));
        PostgresLocalDataSnapshotDriver driver = new PostgresLocalDataSnapshotDriver(environment, commandRunner);

        assertThrows(IllegalStateException.class, () -> driver.dumpDatabase(Path.of("test.dump")));
    }
}

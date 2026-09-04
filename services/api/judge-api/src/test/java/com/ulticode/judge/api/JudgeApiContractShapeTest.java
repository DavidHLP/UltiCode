package com.ulticode.judge.api;

import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeApiContractShapeTest {

    @Test
    void contracts_are_serializable_and_provider_free() {
        assertThat(Serializable.class.isAssignableFrom(JudgeRunCommand.class)).isTrue();
        assertThat(Serializable.class.isAssignableFrom(JudgeRunResult.class)).isTrue();
        Method method = Arrays.stream(JudgeRunService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("execute"))
                .findFirst()
                .orElseThrow();
        assertThat(method.getReturnType()).isEqualTo(RpcResult.class);
        assertThat(method.getParameterTypes()).containsExactly(JudgeRunCommand.class);
        assertThat(JudgeRunCommand.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("spring")
                        || field.getType().getName().contains("mybatis"));
    }

    @Test
    void command_keeps_cases_immutable_and_bounded() {
        JudgeRunCommand.TestCase testCase = new JudgeRunCommand.TestCase(
                "case-1", "Case 1", "ok", null);
        JudgeRunCommand command = new JudgeRunCommand(
                "request-1", 42L, null, "python", "print('ok')",
                java.util.List.of(testCase), null);

        assertThat(command.testCases()).containsExactly(testCase);
        assertThatThrownByMutation(command);
    }

    @Test
    void null_visibility_is_rejected_instead_of_becoming_public() {
        assertThatThrownBy(() -> new JudgeRunCommand(
                "request-1", 42L, "user-1", "python", "print('ok')",
                java.util.List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visibility");
    }

    @Test
    void input_preserves_null_value_for_json_null_literal() {
        JudgeRunCommand.Input input = new JudgeRunCommand.Input(
                "input-1", "Input", "value", null, null);

        assertThat(input.value()).isNull();
    }

    private static void assertThatThrownByMutation(JudgeRunCommand command) {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> command.testCases().add(command.testCases().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

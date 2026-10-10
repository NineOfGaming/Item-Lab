package dev.nineofgaming.itemlab.editor;

import dev.nineofgaming.itemlab.editor.component.EditorCompatibilityCheck;
import dev.nineofgaming.itemlab.editor.component.PendingComponentFormCheck;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeSyntaxLexerCheck;

/** Focused entry points for the recurring headless regression checks. */
public final class EditorRegressionSuite {
    private EditorRegressionSuite() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Choose a regression suite: compatibility or transactions");
        }
        switch (arguments[0]) {
            case "compatibility" -> {
                run("component codecs and data models", () -> EditorCompatibilityCheck.main(new String[0]));
                run("generated-form validation", () -> PendingComponentFormCheck.main(new String[0]));
                run("pending-edit navigation", () -> EditorNavigationGuardCheck.main(new String[0]));
                run("JSON/SNBT syntax", () -> CodeSyntaxLexerCheck.main(new String[0]));
            }
            case "transactions" -> {
                run("transaction composition", () -> PendingEditTransactionCheck.main(new String[0]));
                run("item-stack transactions", () -> PendingItemStackTransactionCheck.main(new String[0]));
                run("real editor recovery", () -> PendingEditorIntegrationCheck.main(new String[0]));
            }
            default -> throw new IllegalArgumentException("Unknown regression suite: " + arguments[0]);
        }
        System.out.println("Passed " + arguments[0] + " regression suite");
    }

    private static void run(String name, Check check) throws Exception {
        System.out.println("Checking " + name);
        check.run();
    }

    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }
}

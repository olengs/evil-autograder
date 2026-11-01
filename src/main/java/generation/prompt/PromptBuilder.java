package generation.prompt;

import java.util.List;

import generation.QuestionSpec;

public final class PromptBuilder { // assembles one prompt > base contract + active add-ons + user text + question context
    private final BasePrompter base = new BasePrompter();

    // build the full prompt for one question
    // activeAddOns resolved by PrompterRegistry > userPrompt free-form >
    // exemplarTester a real tester to imitate
    public String build(QuestionSpec spec, List<AddOnPrompter> activeAddOns,
            String userPrompt, String exemplarTester) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        StringBuilder prompt = new StringBuilder();

        // 1 > mandatory contract first
        prompt.append(base.instructions()).append("\n");

        // 2 > optional add-ons in registry order
        if (activeAddOns != null) {
            for (AddOnPrompter addOn : activeAddOns) {
                prompt.append("\n").append(addOn.instructions()).append("\n");
            }
        }

        // 3 > free-form user instructions when supplied
        if (userPrompt != null && !userPrompt.isBlank()) {
            prompt.append("\nADDITIONAL INSTRUCTIONS FROM THE USER:\n")
                    .append(userPrompt.strip()).append("\n");
        }

        // 4 > the concrete question > exact tester name & class to extend
        prompt.append("\nQUESTION TO WRITE A TESTER FOR:\n");
        prompt.append("- Question id: ").append(spec.getId()).append("\n");
        prompt.append("- Tester class name: ").append(spec.getTesterMainClass()).append("\n");
        prompt.append("- Must declare: public class ").append(spec.getTesterMainClass())
                .append(" extends ").append(spec.getId()).append("\n");

        // 5 > the answer template > authoritative method signature(s) the tester calls
        prompt.append("\nANSWER TEMPLATE SOURCE (the exact method signatures you must call):\n");
        prompt.append("```java\n").append(spec.getAnswerTemplateSource().strip()).append("\n```\n");

        // 6 > the question description from the exam > only when an excerpt was located
        if (spec.hasPdfExcerpt()) {
            prompt.append("\nQUESTION DESCRIPTION FROM THE EXAM:\n");
            prompt.append(spec.getPdfExcerpt().strip()).append("\n");
        }

        // 7 > a real tester as the format to imitate > only when one was supplied
        if (exemplarTester != null && !exemplarTester.isBlank()) {
            prompt.append("\nEXAMPLE OF A CORRECTLY FORMATTED TESTER (imitate this structure, not its content):\n");
            prompt.append("```java\n").append(exemplarTester.strip()).append("\n```\n");
        }

        return prompt.toString();
    }
}

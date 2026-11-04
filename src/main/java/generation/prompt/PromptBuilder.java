package generation.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import generation.QuestionSpec;
import utils.TemplateUtils;

public final class PromptBuilder { // assembles one prompt by filling the section layout from tester-prompt.json
    private final PromptCatalog catalog;

    public PromptBuilder() {
        this(PromptCatalog.loadDefault());
    }

    public PromptBuilder(PromptCatalog catalog) {
        this.catalog = catalog;
    }

    // build the full prompt for one question
    // activeAddOns resolved by PrompterRegistry > userPrompt free-form >
    // exemplarTester a real tester to imitate > examAttached true when the exam pdf is uploaded with the request
    public String build(QuestionSpec spec, List<AddOnPrompter> activeAddOns,
            String userPrompt, String exemplarTester, boolean examAttached) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }

        // every value a section can reference > blank optional values make their section drop out
        Map<String, String> values = new HashMap<>();
        values.put("baseInstructions", catalog.getBaseInstructions().strip());
        values.put("addOnInstructions", activeAddOns == null ? ""
                : activeAddOns.stream().map(addOn -> addOn.instructions().strip())
                        .collect(Collectors.joining("\n\n")));
        values.put("userPrompt", strip(userPrompt));
        values.put("questionId", spec.getId());
        values.put("testerClass", spec.getTesterMainClass());
        values.put("answerTemplate", spec.getAnswerTemplateSource().strip());
        values.put("examAttached", examAttached ? "yes" : "");
        values.put("exemplarTester", strip(exemplarTester));

        // render sections in catalog order > skip a section whose required value is blank > blank line between
        return catalog.getSections().stream()
                .filter(section -> section.requires() == null || !values.getOrDefault(section.requires(), "").isBlank())
                .map(section -> TemplateUtils.fill(PromptCatalog.join(section.lines()), values))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }

    private static String strip(String text) {
        return text == null ? "" : text.strip();
    }
}

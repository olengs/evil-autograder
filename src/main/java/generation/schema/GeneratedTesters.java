package generation.schema;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// the top-level output format sent to the model > one GeneratedTesterSpec per question in a single reply
// a wrapper object because strict json schemas need an object at the root > not a bare array
@JsonClassDescription("Every tester requested in the prompt, one entry per question id.")
public class GeneratedTesters {
    @JsonPropertyDescription("One tester per question id listed in the prompt > every listed id exactly once.")
    public List<GeneratedTesterSpec> testers;
}

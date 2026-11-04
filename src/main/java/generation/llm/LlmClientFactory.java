package generation.llm;

import config.Config;
import config.DotEnv;

public final class LlmClientFactory { //builds the configured LlmClient & reads the api key from env or .env
    private LlmClientFactory() {
    }

    public static LlmClient create(Config config) {
        String provider = config.getLlmProvider();
        String apiKey = readApiKey(config.getLlmApiKeyEnv());

        return switch (provider) {
            case "gemini" -> new GeminiClient(apiKey, config.getLlmBaseUrl());
            // only the real openai platform takes pdf file parts & structured outputs
            case "openai" -> new OpenAiCompatibleClient(apiKey, config.getLlmBaseUrl(), true);
            case "openai-compatible", "ollama" -> new OpenAiCompatibleClient(apiKey, config.getLlmBaseUrl(), false);
            default -> throw new LlmException("unknown llm.provider > " + provider
                    + " > expected gemini | openai | openai-compatible | ollama");
        };
    }

    // the key comes from a real environment variable first > then a git-ignored .env file
    // dotenv-java applies that precedence for us > never from config.properties or source code
    private static String readApiKey(String envVarName) {
        String key = DotEnv.load().get(envVarName);
        if (key == null || key.isBlank()) {
            // report the variable name only > never echo a key value
            throw new LlmException("missing api key > set " + envVarName
                    + " as an environment variable or in a .env file");
        }
        return key;
    }
}


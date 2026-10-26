package dev.infernity.rollplayer.listeners.commands;

import dev.infernity.rollplayer.Resources;
import dev.infernity.rollplayer.components.templates.ErrorTemplate;
import dev.infernity.rollplayer.listeners.templates.SimpleCommandListener;
import dev.infernity.rollplayer.rollplayerlib4.Markup;
import dev.infernity.rollplayer.rollplayerlib4.RollplayerLib4;
import dev.infernity.rollplayer.rollplayerlib4.SpanData;
import dev.infernity.rollplayer.rollplayerlib4.execution.ExecutionResult;
import dev.infernity.rollplayer.rollplayerlib4.execution.Executor;
import dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric.BestPossibleStrategy;
import dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric.WorstPossibleStrategy;
import dev.infernity.rollplayer.rollplayerlib4.lexer.Lexer;
import dev.infernity.rollplayer.rollplayerlib4.lexer.TokenizationResult;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Parser;
import dev.infernity.rollplayer.rollplayerlib4.parsing.ParsingResult;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public class Roll extends SimpleCommandListener {
    public Roll(){
        super("roll", "They see me rollin', they hatin'", "\uD83C\uDFB2");
    }

    @Override
    public List<CommandData> getCommandData(){
        return List.of(
                Commands.slash(commandName, commandDescription)
                        .setContexts(InteractionContextType.ALL)
                        .addOption(OptionType.STRING, "roll", "Roll expressions, rules are explained in rollhelp", true)
                        .addOption(OptionType.STRING, "seed", "A seed to generate this roll with, to ensure consistency.", false)
                        .setIntegrationTypes(IntegrationType.ALL)
                        .setContexts(InteractionContextType.ALL)
        );
    }

    private static boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onCommandRan(@NotNull SlashCommandInteractionEvent event) {
        String input = event.getOption("roll", OptionMapping::getAsString);
        @Nullable String seed = event.getOption("seed", OptionMapping::getAsString);
        try {
            if (isInteger(input)) { // If people are doing "roll 20", they probably expect "roll d20"
                input = "d" + input;
            }
            var evaluation = evaluateRoll(input, seed);
            if (evaluation instanceof ExecutionResult.Error<Double> error) {
                event.replyComponents(ErrorTemplate.of("Evaluation error", formatError(error)))
                        .useComponentsV2().queue();
                return;
            }
            var result = (ExecutionResult.Success<Double>) evaluation;
            List<ContainerChildComponent> output = new ArrayList<>();
            if (!Objects.isNull(seed)){
                output.add(TextDisplay.of("-# **⚠️ Rolling with this seed again will give the same result.**"));
            }
            for (var expression : result.expressions()) {
                if (result.expressions().size() > 1) {
                    var range = expression.source().range();
                    String source = range.originalString().substring(range.cursorStart(), range.cursorEnd());
                    output.add(TextDisplay.ofFormat("**%s**", source));
                }
                output.add(TextDisplay.of(formatExpression(expression)));
            }
            Container outputContainer = createContainer(
                    TextDisplay.ofFormat(
                            "### %s /roll: %s%s",
                                    this.commandEmoji,
                                    input,
                                    Objects.isNull(seed) ? "" : String.format(
                                            ", seed %s",
                                            MarkdownSanitizer.sanitize(
                                                    seed, MarkdownSanitizer.SanitizationStrategy.ESCAPE
                                            )
                                    )
                    ), output)
                    .withAccentColor(resultColor(result));

            event.replyComponents(outputContainer).useComponentsV2().queue();
        } catch (Exception e) {
            var errcode = Resources.getInstance().tryLogException(e, TextDisplay.ofFormat("Roll string: `%s`", input), TextDisplay.ofFormat("-# from `%s`", event.getUser().getName()));
             event.replyComponents(createContainer(
                    TextDisplay.of("**Rollplayer has run into an issue:**"),
                    TextDisplay.ofFormat("%s", e.toString()),
                    TextDisplay.ofFormat("\n-# This issue is unexpected. If you wish to track the issue, join our [support server](https://discord.gg/TT3vyT3tAD) and give the developers the following error code: %s", errcode)
            )).useComponentsV2().queue();
        }
    }

    static ExecutionResult<Double> evaluateRoll(String input, @Nullable String seed) {
        var tokenization = Lexer.tokenize(input);
        if (tokenization instanceof TokenizationResult.Error error) {
            return new ExecutionResult.Error<>(error.message(), error.location());
        }
        var parsing = Parser.parse(((TokenizationResult.Success) tokenization).tokens());
        if (parsing instanceof ParsingResult.Error error) {
            return new ExecutionResult.Error<>(error.message(), error.location());
        }
        if (((ParsingResult.Success) parsing).nodes().size() > 10) {
            return new ExecutionResult.Error<>("Rollplayer cannot roll more than 10 expressions at once",
                    new SpanData(0, input.length(), input));
        }
        return seed == null ? RollplayerLib4.evaluate(input) : RollplayerLib4.evaluate(input, seed);
    }

    static String formatExpression(ExecutionResult.Expression<Double> expression) {
        Node source = expression.source();
        while (source instanceof Node.Group group) {
            source = group.expression();
        }
        if (!(source instanceof Node.Dice)) {
            return "[" + formatNumber(expression.value()) + "]";
        }
        var rolls = expression.rolls().getFirst().values();
        if (rolls.isEmpty()) return "[*No rolls*]";
        var formatted = rolls.stream().map(Roll::formatRoll).toList();
        String line = "[" + String.join(", ", formatted);
        if (rolls.size() > 1) {
            line += " (total: " + formatNumber(expression.value()) + ")";
        }
        return line + "]";
    }

    static String formatError(ExecutionResult.Error<?> error) {
        return error.message() + "\n```\n" + error.location().debugInfo() + "\n```";
    }

    private static String formatRoll(Markup.Group<Double> roll) {
        String marker = (roll.highlight().bold() ? "**" : "") + (roll.highlight().italic() ? "*" : "");
        return marker + formatNumber(roll.value()) + marker;
    }

    private static String formatNumber(double value) {
        if (value == 0) return "0";
        String number = Double.toString(value);
        return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
    }

    static Color resultColor(ExecutionResult.Success<Double> result) {
        var nodes = result.expressions().stream().map(ExecutionResult.Expression::source).toList();
        var executor = new Executor<Double>(nodes, RandomGenerator.getDefault());
        var high = executor.executeDetailed(new BestPossibleStrategy());
        var low = executor.executeDetailed(new WorstPossibleStrategy());
        if (!(high instanceof ExecutionResult.Success<Double> highResult)
                || !(low instanceof ExecutionResult.Success<Double> lowResult)) {
            return Color.GRAY;
        }
        double value = result.values().stream().mapToDouble(Double::doubleValue).sum();
        double highValue = highResult.values().stream().mapToDouble(Double::doubleValue).sum();
        double lowValue = lowResult.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(value) || !Double.isFinite(highValue) || !Double.isFinite(lowValue)) {
            return Color.GRAY;
        }
        double min = Math.min(lowValue, highValue);
        double max = Math.max(lowValue, highValue);
        if (!Double.isFinite(max - min)) return Color.GRAY;
        if (min == 0 && max == 0) return Color.BLACK;
        if (value < min || (value == min && min != max)) return Color.BLACK;
        if (value >= max) {
            double width = max > min ? max - min : Math.abs(max);
            double over = Math.min(1, (value - max) / width);
            return Color.getHSBColor((float) (.5 + over * (300d / 360 - .5)), 1, (float) (.75 + over * .1));
        }
        float position = (float) ((value - min) / (max - min));
        return Color.getHSBColor(position * (120f / 360), 1, .7f);
    }
}

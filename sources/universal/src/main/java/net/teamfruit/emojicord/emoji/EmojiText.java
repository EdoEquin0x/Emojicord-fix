package net.teamfruit.emojicord.emoji;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import net.teamfruit.emojicord.emoji.StandardEmojiIdDictionary.StandardEmojiIdRepository;

public class EmojiText {
	public static final @Nonnull Function<Integer, String> placeHolderSupplier = e -> String.format("{%d}", e);
	public static final @Nonnull Pattern placeHolderPattern = Pattern.compile("\\{(\\d+?)\\}");

	public final @Nonnull String text;
	public final @Nonnull ImmutableList<EmojiTextElement> emojis;

	public EmojiText(final String text, final ImmutableList<EmojiTextElement> emojis) {
		this.text = text;
		this.emojis = emojis;
	}

	public static EmojiText createUnparsed(final String text) {
		return new EmojiText(text, ImmutableList.of());
	}

	public static EmojiText createEscaped(final String text) {
		EmojiText emojiText = EmojiText.createUnparsed(text);
		emojiText = EmojiTextParser.escape(emojiText);
		return emojiText;
	}

	public static EmojiText createEncoded(final String text) {
		EmojiText emojiText = EmojiText.createUnparsed(text);
		emojiText = EmojiTextParser.escape(emojiText);
		emojiText = EmojiTextParser.encode(emojiText);
		return emojiText;
	}

	public static EmojiText createParsed(final String text) {
		EmojiText emojiText = EmojiText.createUnparsed(text);
		emojiText = EmojiTextParser.escape(emojiText);
		emojiText = EmojiTextParser.encode(emojiText);
		emojiText = EmojiTextParser.parse(emojiText);
		return emojiText;
	}

	public String getEncoded() {
		final Matcher matcher = placeHolderPattern.matcher(this.text);
		final StringBuffer sb = new StringBuffer();
		if (matcher.find()) {
			final int emojiIndex = NumberUtils.toInt(matcher.group(1), -1);
			if (0<=emojiIndex&&emojiIndex<this.emojis.size()) {
				final EmojiTextElement entry = this.emojis.get(emojiIndex);
				matcher.appendReplacement(sb, entry.encoded);
			}
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	public EmojiContext getEmojiContext() {
		final Map<Integer, EmojiTextElement> emojiMap = Maps.newHashMap();
		final Matcher matcher = EmojiText.placeHolderPattern.matcher(this.text);
		final StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			final int emojiIndex = NumberUtils.toInt(matcher.group(1), -1);
			if (0<=emojiIndex&&emojiIndex<this.emojis.size()) {
				final EmojiTextElement entry = this.emojis.get(emojiIndex);
				if (entry.id==null)
					matcher.appendReplacement(sb, entry.raw);
				else {
					matcher.appendReplacement(sb, "?");
					final int index = sb.length()-"?".length();
					emojiMap.put(index, entry);
					//if (isTextFieldRendering)
					//	sb.append(entry.raw);
				}
			}
		}
		matcher.appendTail(sb);
		return new EmojiContext(sb.toString(), emojiMap);
	}

	public static class EmojiTextElement {
		public final EmojiId id;
		public final String raw;
		public final String encoded;

		public EmojiTextElement(final EmojiId id, final String raw, final String encoded) {
			this.id = id;
			this.raw = raw;
			this.encoded = encoded;
		}
	}

	public static class EmojiTextBuilder {
		public final Matcher matcher;
		private final StringBuffer sb;
		private final ImmutableList.Builder<EmojiTextElement> emojis;
		private int size;

		private EmojiTextBuilder(final Matcher matcher, final StringBuffer sb, final ImmutableList.Builder<EmojiTextElement> emojis, final int size) {
			this.matcher = matcher;
			this.sb = sb;
			this.emojis = emojis;
			this.size = size;
		}

		public EmojiTextBuilder append(final EmojiTextElement element) {
			this.matcher.appendReplacement(this.sb, placeHolderSupplier.apply(this.size++));
			this.emojis.add(element);
			return this;
		}

		public EmojiText build() {
			this.matcher.appendTail(this.sb);
			return new EmojiText(this.sb.toString(), this.emojis.build());
		}

		public EmojiText apply(final EmojiTextTransformer transformer) {
			final EmojiTextGroupSupplierImpl supplier = new EmojiTextGroupSupplierImpl();
			while (this.matcher.find()) {
				final EmojiTextElement element = transformer.transform(supplier);
				if (element!=null)
					append(element);
			}
			return build();
		}

		public EmojiText apply(final EmojiTextTransformer... transformers) {
			final EmojiTextGroupSupplierImpl supplier = new EmojiTextGroupSupplierImpl();
			while (this.matcher.find())
				for (final EmojiTextTransformer transformer : transformers) {
					final EmojiTextElement element = transformer.transform(supplier);
					if (element!=null) {
						append(element);
						continue;
					}
				}
			return build();
		}

		public interface EmojiTextGroupSupplier {
			String group(int i);
		}

		private class EmojiTextGroupSupplierImpl implements EmojiTextGroupSupplier {
			@Override
			public String group(final int i) {
				return EmojiTextBuilder.this.matcher.group(i);
			}
		}

		public interface EmojiTextTransformer {
			@Nullable
			EmojiTextElement transform(EmojiTextGroupSupplier supplier);
		}

		public static EmojiTextBuilder builder(final Pattern pattern, final EmojiText emojiText) {
			return new EmojiTextBuilder(pattern.matcher(emojiText.text),
					new StringBuffer(),
					ImmutableList.<EmojiTextElement> builder().addAll(emojiText.emojis),
					emojiText.emojis.size());
		}
	}

	public static class EmojiTextParser {
		static final @Nonnull Pattern colorPattern = Pattern.compile("(?i)\u00A7([0-9A-FK-OR])");
		// (?:(?i)§[0-9A-FK-OR])|<a?\:(?:\w+?)\:([a-zA-Z0-9+/=]+?)>|\:([\w+-]+?)\:(?:\:skin-tone-(\d)\:)?
		static final @Nonnull Pattern pattern = Pattern.compile("<a?\\:(\\w+?)\\:([a-zA-Z0-9+/=]+?)>|\\:([\\w+-]+?)\\:(?:\\:skin-tone-(\\d)\\:)?");

		public static EmojiText escape(EmojiText emojiText) {
			emojiText = EmojiTextBuilder.builder(placeHolderPattern, emojiText).apply(matcher -> {
				final String g0 = matcher.group(0);
				return new EmojiTextElement(null, g0, g0);
			});
			emojiText = EmojiTextBuilder.builder(colorPattern, emojiText).apply(matcher -> {
				final String g0 = matcher.group(0);
				return new EmojiTextElement(null, g0, g0);
			});
			return emojiText;
		}

		public static EmojiText parse(EmojiText emojiText) {
			emojiText = EmojiTextBuilder.builder(pattern, emojiText).apply(matcher -> {
				final String g0 = matcher.group(0);
				final String g2 = matcher.group(2);
				if (!StringUtils.isEmpty(g2))
					if (StringUtils.length(g2)>12)
						return new EmojiTextElement(EmojiId.DiscordEmojiId.fromDecimalId(g2), g0, g0);
					else
						return new EmojiTextElement(EmojiId.DiscordEmojiId.fromEncodedId(g2), g0, g0);
				return null;
			}, matcher -> {
				final String g0 = matcher.group(0);
				final String g3 = matcher.group(3);
				final String g4 = matcher.group(4);
				if (!StringUtils.isEmpty(g3))
					if (!StringUtils.isEmpty(g4)) {
						EmojiId emojiId = EmojiId.StandardEmojiId.fromAlias(g3+":skin-tone-"+g4);
						if (emojiId==null)
							emojiId = EmojiId.StandardEmojiId.fromAlias(g3);
						return new EmojiTextElement(emojiId, g0, g0);
					} else
						return new EmojiTextElement(EmojiId.StandardEmojiId.fromAlias(g3), g0, g0);
				return null;
			});
			return emojiText;
		}

		public static EmojiText encode(EmojiText emojiText) {
			emojiText = EmojiTextBuilder.builder(pattern, emojiText).apply(matcher -> {
				final String g0 = matcher.group(0);
				final String g3 = matcher.group(3);
				if (!StringUtils.isEmpty(g3))
					if (EmojiId.StandardEmojiId.fromAlias(g3)==null) {
						final EmojiId emojiId = DiscordEmojiIdDictionary.instance.get(g3);
						if (emojiId instanceof EmojiId.DiscordEmojiId)
							return new EmojiTextElement(emojiId, g0, String.format("<:%s:%s>", g3, ((EmojiId.DiscordEmojiId) emojiId).getEncodedId()));
					}
				return null;
			});
			emojiText = EmojiTextBuilder.builder(StandardEmojiIdRepository.instance.shortAliasPattern, emojiText).apply(matcher -> {
				final String g0 = matcher.group(0);
				final EmojiId emojiId = EmojiId.StandardEmojiId.fromAlias(g0);
				if (emojiId!=null)
					return new EmojiTextElement(emojiId, g0, String.format(":%s:", emojiId.getCacheName()));
				return null;
			});
			emojiText = EmojiTextBuilder.builder(StandardEmojiIdRepository.instance.utfPattern, emojiText).apply(matcher -> {
				final String g0 = matcher.group(0);
				final EmojiId emojiId = EmojiId.StandardEmojiId.fromUtf(g0);
				if (emojiId!=null)
					return new EmojiTextElement(emojiId, g0, String.format(":%s:", emojiId.getCacheName().replace(":", "::")));
				return null;
			});
			return emojiText;
		}
	}
}

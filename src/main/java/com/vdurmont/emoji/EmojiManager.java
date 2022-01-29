/*
Apache Commons Text
Copyright 2014-2020 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (https://www.apache.org/).
*/

package com.vdurmont.emoji;

import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Holds the loaded emojis and provides search functions.
 *
 * @author Vincent DURMONT [vdurmont@gmail.com]
 */
public class EmojiManager {
  private static final String PATH = "/emojis.json";
  private static final String EMOJIS_SER_FILE = "emojis.ser";
  private static Map<String, Map<String, Emoji>> EMOJIS_BY_TAG_ALIAS =
    new HashMap<String, Map<String, Emoji>>();
  private static final Map<String, Set<Emoji>> EMOJIS_BY_TAG =
    new HashMap<String, Set<Emoji>>();
  private static final List<Emoji> ALL_EMOJIS;
  static final EmojiTrie EMOJI_TRIE;

    // Tag string constants
    public static final String TAG_NONE = "_none";

  static {
    try {
      EMOJIS_BY_TAG_ALIAS.put(TAG_NONE, new HashMap<String, Emoji>());

      InputStream stream = EmojiLoader.class.getResourceAsStream(PATH);
      List<Emoji> emojis = EmojiLoader.loadEmojis(stream);
      ALL_EMOJIS = emojis;
      for (Emoji emoji : emojis) {
          for (String tag : emoji.getTags()) {
              if (EMOJIS_BY_TAG.get(tag) == null) {
                  EMOJIS_BY_TAG.put(tag, new HashSet<Emoji>());
              }
              EMOJIS_BY_TAG.get(tag).add(emoji);

              if (!EMOJIS_BY_TAG_ALIAS.containsKey(tag)) {
                  EMOJIS_BY_TAG_ALIAS.put(tag, new HashMap<String, Emoji>());
              }
          }
      }

      for (Emoji emoji : emojis) {
          if (emoji.getTags().isEmpty()) {
              for (String alias : emoji.getAliases()) {
                  EMOJIS_BY_TAG_ALIAS.get(TAG_NONE).put(alias, emoji);
              }
          } else {
              for (String tag : emoji.getTags()) {
                  for (String alias : emoji.getAliases()) {
                      EMOJIS_BY_TAG_ALIAS.get(tag).put(alias, emoji);
                  }
              }
          }
      }

      EMOJI_TRIE = new EmojiTrie(emojis);
      Collections.sort(ALL_EMOJIS, new Comparator<Emoji>() {
        public int compare(Emoji e1, Emoji e2) {
          return e2.getUnicode().length() - e1.getUnicode().length();
        }
      });
      stream.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * No need for a constructor, all the methods are static.
   */
  private EmojiManager() {}

  /**
   * Returns all the {@link com.vdurmont.emoji.Emoji}s for a given tag.
   *
   * @param tag the tag
   *
   * @return the associated {@link com.vdurmont.emoji.Emoji}s, null if the tag
   * is unknown
   */
  public static Set<Emoji> getForTag(String tag) {
    if (tag == null) {
      return null;
    }
    return EMOJIS_BY_TAG.get(tag);
  }

  /**
   * Returns the {@link com.vdurmont.emoji.Emoji} for a given alias with any tag.
   *
   * @param alias the alias
   *
   * @return the associated {@link com.vdurmont.emoji.Emoji}, null if the alias
   * is unknown
   */
  public static Emoji getForAlias(String alias) {
    if (alias == null || alias.isEmpty()) {
      return null;
    }

    Emoji out = null;
    for (String tag : EMOJIS_BY_TAG_ALIAS.keySet()) {
        out = EMOJIS_BY_TAG_ALIAS.get(tag).get(trimAlias(alias));
        if (out != null) {
            break;
        }
    }
    return out;
  }

    /**
     * Returns the {@link com.vdurmont.emoji.Emoji} for a given alias with a given tag.
     *
     * @param alias the alias
     * @param tag the tag
     *
     * @return the associated {@link com.vdurmont.emoji.Emoji}, null if the alias
     * is unknown
     */
    public static Emoji getForAliasWithTag(String alias, String tag) {
        if (alias == null) {
            return null;
        }

        Map<String, Emoji> tagMap = EMOJIS_BY_TAG_ALIAS.get(tag);
        if (tagMap == null) {
            return null;
        } else {
            return tagMap.get(trimAlias(alias));
        }
    }

    /**
     * Enum of similarity algorithms to choose from
     */
    public enum SimilarityAlgorithm {
        LEVENSHTEIN, FUZZY
    }
    /**
     * Returns the {@link com.vdurmont.emoji.Emoji} for a given alias.
     * Uses a {@link com.vdurmont.emoji.EmojiManager.SimilarityAlgorithm} to find the closest alias.
     *
     * @param alias the alias
     * @param algorithm the text similarity algorithm
     * @param threshold the similarity threshold as a percentage of the input word, from 0.0 - 1.0
     *
     * @return the associated {@link com.vdurmont.emoji.Emoji}, null if the alias
     * is unknown
     */
    public static Emoji getForAliasWithSimilarity(String alias, SimilarityAlgorithm algorithm, float threshold) {
        if (alias == null) {
            return null;
        }

        Emoji out = null;
        Set<String> aliasSet = new HashSet<String>();
        for (String tag : EMOJIS_BY_TAG_ALIAS.keySet()) {
            out = EMOJIS_BY_TAG_ALIAS.get(tag).get(trimAlias(alias));

            if (out != null) {
                // Found
                return out;
            } else {
                // Failed initial check -> use spellchecking
                 aliasSet.addAll(EMOJIS_BY_TAG_ALIAS.get(tag).keySet());
            }
        }

        return getForAliasWithSimilarity(getClosestString(aliasSet, alias, algorithm, threshold), null, 0);
    }

    /**
     * Returns the {@link com.vdurmont.emoji.Emoji} for a given alias with a given tag.
     * Uses a {@link com.vdurmont.emoji.EmojiManager.SimilarityAlgorithm} to find the closest alias.
     *
     * @param alias the alias
     * @param tag the tag
     * @param algorithm the text similarity algorithm
     * @param threshold the similarity threshold as a percentage of the input word, from 0.0 - 1.0
     *
     * @return the associated {@link com.vdurmont.emoji.Emoji}, null if the alias
     * is unknown
     */
    public static Emoji getForAliasWithTagAndSimilarity(String alias, String tag, SimilarityAlgorithm algorithm, float threshold) {
        if (alias == null) {
            return null;
        }

        Map<String, Emoji> tagMap = EMOJIS_BY_TAG_ALIAS.get(tag);
        if (tagMap == null) {
            return null;
        } else {
            Emoji initialCheck = tagMap.get(trimAlias(alias));
            // Failed initial check -> use spellchecking
            if (initialCheck == null) {
                Set<String> aliasSet = tagMap.keySet();
                return tagMap.get(getClosestString(aliasSet, alias, algorithm, threshold));
            }

            return initialCheck;
        }
    }

    private static String getClosestString(Set<String> wordSet, String queryStr, SimilarityAlgorithm algorithm, float threshold) {
        int thresholdAsNumLetters = Math.round((1.0f - threshold) * queryStr.length());

        Integer bestSimilarity = null;
        String bestWord = null;

        for (String storedAlias : wordSet) {
            int sim;
            switch (algorithm) {
                case LEVENSHTEIN:
                    sim = new LevenshteinDistance().apply(queryStr, storedAlias);

                    if (bestSimilarity == null || bestSimilarity > sim) {
                        // Found better
                        bestSimilarity = sim;
                        bestWord = storedAlias;
                    }
                    break;
                case FUZZY:
                    sim = new FuzzyScore(Locale.getDefault()).fuzzyScore(queryStr, storedAlias);

                    if (bestSimilarity == null || bestSimilarity < sim) { // Fuzzy score is opposite direction of Levenshtein
                        // Found better
                        bestSimilarity = sim;
                        bestWord = storedAlias;
                    }
                    break;
                default:
                    System.out.println("Invalid algorithm: " + algorithm + ".");
                    return null;
            }

        }

        if (bestSimilarity == null) {
            // Nothing close found, or best similarity score does not meet threshold
            return null;
        }

        boolean similarityMeetThreshold = false;
        switch (algorithm) {
            case LEVENSHTEIN:
                similarityMeetThreshold = bestSimilarity < thresholdAsNumLetters;
                break;
            case FUZZY:
                similarityMeetThreshold = bestSimilarity > thresholdAsNumLetters;
                break;
        }

        if (similarityMeetThreshold) {
            return bestWord;
        } else {
            return null;
        }
    }

  private static String trimAlias(String alias) {
      String result = alias;
      if (result.startsWith(":")) {
          result = result.substring(1, result.length());
      }
      if (result.endsWith(":")) {
          result = result.substring(0, result.length() - 1);
      }
      return result;
  }


  /**
   * Returns the {@link com.vdurmont.emoji.Emoji} for a given unicode.
   *
   * @param unicode the the unicode
   *
   * @return the associated {@link com.vdurmont.emoji.Emoji}, null if the
   * unicode is unknown
   */
  public static Emoji getByUnicode(String unicode) {
    if (unicode == null) {
      return null;
    }
    return EMOJI_TRIE.getEmoji(unicode);
  }

  /**
   * Returns all the {@link com.vdurmont.emoji.Emoji}s
   *
   * @return all the {@link com.vdurmont.emoji.Emoji}s
   */
  public static Collection<Emoji> getAll() {
    return ALL_EMOJIS;
  }

  /**
   * Tests if a given String is an emoji.
   *
   * @param string the string to test
   *
   * @return true if the string is an emoji's unicode, false else
   */
  public static boolean isEmoji(String string) {
    if (string == null) return false;

    EmojiParser.UnicodeCandidate unicodeCandidate = EmojiParser.getNextUnicodeCandidate(string.toCharArray(), 0);
    return unicodeCandidate != null &&
            unicodeCandidate.getEmojiStartIndex() == 0 &&
            unicodeCandidate.getFitzpatrickEndIndex() == string.length();
  }

  /**
   * Tests if a given String contains an emoji.
   *
   * @param string the string to test
   *
   * @return true if the string contains an emoji's unicode, false otherwise
   */
  public static boolean containsEmoji(String string) {
    if (string == null) return false;

    return EmojiParser.getNextUnicodeCandidate(string.toCharArray(), 0) != null;
  }

  /**
   * Tests if a given String only contains emojis.
   *
   * @param string the string to test
   *
   * @return true if the string only contains emojis, false else
   */
  public static boolean isOnlyEmojis(String string) {
    return string != null && EmojiParser.removeAllEmojis(string).isEmpty();
  }

  /**
   * Checks if sequence of chars contain an emoji.
   * @param sequence Sequence of char that may contain emoji in full or
   * partially.
   *
   * @return
   * &lt;li&gt;
   *   Matches.EXACTLY if char sequence in its entirety is an emoji
   * &lt;/li&gt;
   * &lt;li&gt;
   *   Matches.POSSIBLY if char sequence matches prefix of an emoji
   * &lt;/li&gt;
   * &lt;li&gt;
   *   Matches.IMPOSSIBLE if char sequence matches no emoji or prefix of an
   *   emoji
   * &lt;/li&gt;
   */
  public static EmojiTrie.Matches isEmoji(char[] sequence) {
    return EMOJI_TRIE.isEmoji(sequence);
  }

  /**
   * Returns all the tags in the database
   *
   * @return the tags
   */
  public static Collection<String> getAllTags() {
    return EMOJIS_BY_TAG.keySet();
  }
}

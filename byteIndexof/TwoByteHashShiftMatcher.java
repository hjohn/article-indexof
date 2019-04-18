package byteIndexof;

import java.util.Arrays;

/**
 * Similar to a last byte matcher, except this matches two bytes at once.  Normally this
 * would require a shift table of 65536 entries, but with some hashing of the two bytes
 * involved, the table can be kept small.  If there is a hash collision for two pairs of
 * bytes, the most conservative shift value is stored in the table.<p>
 *
 * Performance improves with a larger hash table, or potentially a better hashing
 * algorithm (currently it shifts one of the bytes and xors it with the other).
 *
 * @author John Hendrikx
 */
public class TwoByteHashShiftMatcher extends MatcherFactory
{
    static long count = 0;
    static long compare_count = 0;
    static long total_shift = 0;

    /**
     * Controls how much memory is allocated for hash table, 0 = 1kB, 1 = 2kB, 2 = 4kB, etc.
     *
     * Generally, higher is better, until caches can't hold the table.
     */
    private static int p2 = 4;

    private static final class MatcherImpl extends Matcher
    {
        private final byte[] pattern;
        private final int[] shifts;

        public MatcherImpl(byte [] pattern, int[] shifts) {
            this.pattern = pattern;
            this.shifts = shifts;
        }

        @Override
        public int indexOf (byte[] text, int fromIdx) {
            int pattern_len = pattern.length;
            int text_len = text.length;
            int offset = pattern_len - 1;
            int i = fromIdx + offset;
            int maxLen = text_len - pattern_len + offset;

            while(i < maxLen) {
              int hash = ((text[i - 1] & 0xff) << p2) ^ (text[i] & 0xff);
              int skip = shifts[hash];

              if (DEBUG) {
                  ++ count;
                  total_shift += skip;
                  if (skip == 0) ++ compare_count;
              }
              
              if(skip == 0) {  // No skip, let's compare
                if(compare (text, i - offset, pattern, pattern_len)) {
                  return i - offset;
                }
                i++;  // Compare failed, move ahead 1.
              }

              i += skip;  // Can be done always, if skip was zero it does nothing.
            }

            return -1;
        }
    }

    @Override
    public Matcher createMatcher (byte[] pattern)
    {
      int[] shifts = new int[256 << p2];

      Arrays.fill(shifts, pattern.length);  // Fill hash table with the maximum allowed shift for all entries

      // Overwrite entries part of the pattern with lower shift values:
      for(int j = pattern.length - 2; j >= 0; j--) {
        int shift = pattern.length - 2 - j;
        int hash = ((pattern[j] & 0xff) << p2) ^ (pattern[j + 1] & 0xff);

        if(shifts[hash] > shift) {  // Because there can be collisions in the hash, take the most conservative shift value
          shifts[hash] = shift;
        }
      }
      
      // Overwrite all pairs which end with the first character of our pattern with a shift value of pattern.length - 1:
      for(int i = -128; i < 128; i++) {
        int shift = pattern.length - 1;
        int hash = ((i & 0xff) << p2) ^ (pattern[0] & 0xff);

        if(shifts[hash] > shift) {  // Because there can be collisions in the hash, take the most conservative shift value
          shifts[hash] = shift;
        }
      }

      return new MatcherImpl(pattern, shifts);
    }

    @Override
    public String toString ()
    {
        return this.getClass ().getSimpleName () + "(" + p2 + ")";
    }
    
    @Override
    public String stats ()
    {
        if (count == 0) {
            return "";
        }
        double avg_shift = total_shift * 1.0 / count;
        double compare_rate = compare_count * 100.0 / count;
        compare_count = count = total_shift = 0;
        return String.format ("; compare rate: %5.2f; avg shift=%5.2f", compare_rate, avg_shift);
    }
}

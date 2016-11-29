package morfologik.speller;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.carrotsearch.randomizedtesting.RandomizedTest;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryMetadata;
import morfologik.stemming.EncoderType;
import morfologik.tools.ExitStatus;
import morfologik.tools.FSACompile;
import morfologik.tools.SerializationFormat;

public class FindReplacementsBugTest extends RandomizedTest {
  @Test
  public void testSeparatorInEncoded() throws Exception {
    final Path tmpDir = newTempDir().toPath();
    final Path input = tmpDir.resolve("dictionary.input");
    final Path dict = tmpDir.resolve("dictionary.dict");
    final Path metadata = DictionaryMetadata.getExpectedMetadataLocation(input);

    char separator = '_';
    try (Writer writer = Files.newBufferedWriter(metadata, StandardCharsets.UTF_8)) {
      DictionaryMetadata.builder()
          .separator(separator)
          .encoder(EncoderType.SUFFIX)
          .encoding(StandardCharsets.UTF_8)
          .build()
          .write(writer);
    }

    Set<String> sequences = new LinkedHashSet<>();
    for (int seqs = randomIntBetween(0, 100); --seqs >= 0;) {
      sequences.add("eta_I");
      sequences.add("eprom_G");
    }

    try (Writer writer = Files.newBufferedWriter(input, StandardCharsets.UTF_8)) {
      for (String in : sequences) {
        writer.write(in);
        writer.write('\n');
      }
    }
    Assertions.assertThat(new FSACompile(input, dict, SerializationFormat.CFSA2, false, false, false).call())
      .isEqualTo(ExitStatus.SUCCESS);

    Assertions.assertThat(dict).isRegularFile();
    
    final Speller speller = new Speller(Dictionary.read(dict), 1);
    System.out.println(speller.findReplacements("eti"));
    Assertions.assertThat(speller.findReplacements("eti").get(0).equals("eta")).isTrue();
    speller.findReplacements("eta_I");
    System.out.println(speller.findReplacements("eti"));
    System.out.println(speller.findReplacements("eti_i"));    
    
    // Failing Test !?
    Assertions.assertThat(speller.findReplacements("eti").get(0).equals("eta")).isTrue();   

  }
}

package morfologik.tools;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;

import morfologik.fsa.*;
import morfologik.stemming.*;
import morfologik.util.FileUtils;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * This utility will dump the information and contents of a given {@link FSA}
 * dictionary. It can dump dictionaries in the raw form (as fed to the
 * <code>fsa_build</code> program) or decoding compressed stem forms.
 */
public final class DumpTool extends Tool {
	/**
	 * Writer used to print messages and dictionary dump.
	 */
	private OutputStream writer;

	/**
	 * Print raw data only, no headers.
	 */
	private boolean dataOnly;

	/**
	 * Decode encoded forms if present in the dictionary.
	 */
	private boolean decode;

	/**
	 * Command line entry point after parsing arguments.
	 */
	protected void go(CommandLine line) throws Exception {
		final File dictionaryFile = (File) line
		        .getParsedOptionValue(SharedOptions.fsaDictionaryFileOption
		                .getOpt());

		dataOnly = line.hasOption(SharedOptions.dataOnly.getOpt());
		decode = line.hasOption(SharedOptions.decode.getOpt());

		FileUtils.assertExists(dictionaryFile, true, false);

		dump(dictionaryFile, dataOnly);
	}

	/**
	 * Dumps the content of a dictionary to a file.
	 */
	private void dump(File dictionaryFile, boolean dataOnly)
	        throws UnsupportedEncodingException, IOException {
		final long start = System.currentTimeMillis();

		final Dictionary dictionary;
		final FSA fsa;

		if (!dictionaryFile.canRead()) {
			printWarning("Dictionary file does not exist: "
			        + dictionaryFile.getAbsolutePath());
			return;
		}

		this.writer = new BufferedOutputStream(System.out, 1024 * 32);

		if (hasMetadata(dictionaryFile)) {
			dictionary = Dictionary.read(dictionaryFile);
			fsa = dictionary.fsa;

			final String encoding = dictionary.metadata.encoding;
			if (!Charset.isSupported(encoding)) {
				printWarning("Dictionary's charset is not supported "
				        + "on this JVM: " + encoding);
				return;
			}
		} else {
			dictionary = null;
			fsa = FSA.getInstance(new FileInputStream(dictionaryFile));
			printWarning("Warning: FSA automaton without metadata file.");
		}

		printExtra("FSA properties");
		printExtra("--------------------");
		printExtra("FSA implementation  : " + fsa.getClass().getName());
		printExtra("Compiled with flags : " + fsa.getFlags().toString());

		if (!dataOnly) {
    		final FSAInfo info = new FSAInfo(fsa);
    		printExtra("Number of arcs      : " 
    				+ info.arcsCount + "/" + info.arcsCountTotal);
    		printExtra("Number of nodes     : " + info.nodeCount);
    		printExtra("Number of final st. : " + info.finalStatesCount);
    		printExtra("");
		}

		// Separator for dumping.
		char separator = '\t';

		if (fsa instanceof FSA5) {
			printExtra("FSA5 properties");
			printExtra("--------------------");
			printFSA5((FSA5) fsa);			
			printExtra("");
		}

		if (fsa instanceof CFSA) {
			printExtra("CFSA properties");
			printExtra("--------------------");
			printCFSA((CFSA) fsa);
			printExtra("");
		}

		if (dictionary != null) {
			printExtra("Dictionary metadata");
			printExtra("--------------------");
			printExtra("Encoding            : " + dictionary.metadata.encoding);
			printExtra("Separator byte      : 0x"
			        + Integer.toHexString(dictionary.metadata.separator)
			        + " ('" + decodeSeparator(dictionary) + "')");
			printExtra("Uses prefixes       : "
			        + dictionary.metadata.usesPrefixes);
			printExtra("Uses infixes        : "
			        + dictionary.metadata.usesInfixes);
			printExtra("");

			printExtra("Dictionary metadata (all keys)");
			printExtra("---------------------------------");

			for (Map.Entry<String, String> e : dictionary.metadata.metadata
			        .entrySet()) {
				printExtra(String
				        .format("%-27s : %s", e.getKey(), e.getValue()));
			}
			printExtra("");
		}

		int sequences = 0;
		if (decode) {
			if (dictionary == null) {
				printWarning("No dictionary metadata available.");
				return;
			}

			printExtra("Decoded FSA data (in the encoding above)");
			printExtra("----------------------------------------");

			final DictionaryLookup dl = new DictionaryLookup(dictionary);
			final StringBuilder builder = new StringBuilder();
			final OutputStreamWriter osw = new OutputStreamWriter(writer,
			        dictionary.metadata.encoding);

			CharSequence t;
			for (WordData wd : dl) {
				builder.setLength(0);
				builder.append(wd.getWord());
				builder.append(separator);

				t = wd.getStem();
				if (t == null)
					t = "";
				builder.append(t);
				builder.append(separator);

				t = wd.getTag();
				if (t == null)
					t = "";
				builder.append(t);
				builder.append('\n');

				osw.write(builder.toString());
				sequences++;
			}
			osw.flush();
		} else {
			printExtra("FSA data (raw bytes in the encoding above)");
			printExtra("------------------------------------------");

			for (ByteBuffer bb : fsa) {
				writer.write(bb.array(), 0, bb.remaining());
				writer.write(0x0a);
				sequences++;
			}
		}

		printExtra("--------------------");

		final long millis = Math.max(1, System.currentTimeMillis() - start);
		printExtra(String
		        .format(
		                Locale.ENGLISH,
		                "Dictionary dumped in %.3f second(s), %d sequences (%d sequences/sec.).",
		                millis / 1000.0, sequences,
		                (int) (sequences / (millis / 1000.0))));

		writer.flush();
	}

	/**
	 * Convert a byte to a character using the given encoding.
	 */
	private char byteAsChar(byte v, String encoding) {
		try {
			final String chr = new String(new byte[] { v }, encoding);
			if (chr.length() != 1) {
				throw new RuntimeException(
				        "Unexpected annotation character length (should be 1): "
				                + chr.length());
			}
			return chr.charAt(0);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
    }

	/**
	 * Print {@link CFSA}-specific stuff.
	 */
	private void printCFSA(CFSA fsa) throws IOException {
		printExtra("GTL                 : " + fsa.gtl);
		printExtra("Node extra data     : " + fsa.nodeDataLength);
		printExtra("Label mapping       : " + bytesAsChars(fsa.labelMapping));
    }

	/**
	 * 
	 */
	private String bytesAsChars(byte[] bytes) {
		char [] chars = new char [bytes.length];
		
		for (int i = 0; i < bytes.length; i++)
			chars[i] = byteAsChar(bytes[i]);

	    return new String(chars);
    }

	/**
	 * Print {@link FSA5}-specific stuff.
	 */
	private void printFSA5(FSA5 fsa) throws IOException {
		printExtra("GTL                 : " + fsa.gtl);
		printExtra("Node extra data     : " + fsa.nodeDataLength);
		printExtra("Annotation separator: " + byteAsChar(fsa.annotation));
		printExtra("Filler character    : " + byteAsChar(fsa.filler));
    }

	/**
	 * Convert a byte to a character, no charset decoding, simple ASCII range mapping.
	 */
	private char byteAsChar(byte v) {
		char chr = (char) (v & 0xff);
		if (chr < 127)
			return chr;
		else
			return '?';
    }

	/*
     * 
     */
	private void printExtra(String msg) throws IOException {
		if (dataOnly)
			return;
		writer.write(msg.getBytes());
		writer.write(0x0a);
	}

	/*
     * 
     */
	private void printWarning(String msg) throws IOException {
		System.err.println(msg);
	}

	/*
     * 
     */
	private String decodeSeparator(Dictionary dictionary) {
		try {
			return new String(new byte[] { dictionary.metadata.separator },
			        dictionary.metadata.encoding);
		} catch (UnsupportedEncodingException e) {
			return "<unsupported encoding: " + dictionary.metadata.encoding
			        + ">";
		}
	}

	/**
	 * Check if there is a metadata file for the given FSA automaton.
	 */
	private static boolean hasMetadata(File fsaFile) {
		final File featuresFile = new File(fsaFile.getParent(), Dictionary
		        .getExpectedFeaturesName(fsaFile.getName()));

		return featuresFile.canRead();
	}

	/**
	 * Command line options for the tool.
	 */
	protected void initializeOptions(Options options) {
		options.addOption(SharedOptions.fsaDictionaryFileOption);
		options.addOption(SharedOptions.dataOnly);
		options.addOption(SharedOptions.decode);
	}

	/**
	 * Command line entry point.
	 */
	public static void main(String[] args) throws Exception {
		final DumpTool fsaDump = new DumpTool();
		fsaDump.go(args);
	}
}
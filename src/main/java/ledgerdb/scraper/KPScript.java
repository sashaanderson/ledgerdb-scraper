package ledgerdb.scraper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.io.IOUtils;

public class KPScript {

    public static final String DEFAULT_KDBX_FILE = "ledgerdb-scraper.kdbx";
    
    private static final String EOL = System.lineSeparator();
    
    private static final Logger logger = LogManager.getLogger();
    
    private KPScript() {}
    
    public static String getEntry(String title, String field) throws IOException {
        String kdbxFile = System.getProperty("kdbx.file");
        if (kdbxFile == null)
            kdbxFile = DEFAULT_KDBX_FILE;
        if (!Files.exists(Paths.get(kdbxFile)))
            throw new FileNotFoundException(kdbxFile);
        
        String kdbxPw = System.getProperty("kdbx.pw");
        if (kdbxPw != null && kdbxPw.startsWith("@")) {
            kdbxPw = Files.readAllLines(Paths.get(kdbxPw.substring(1))).get(0);
        }
        
        ArrayList<String> args = new ArrayList<>();
        args.add("KPScript");
        args.add("-c:GetEntryString");
        args.add(kdbxFile);
        args.add("-pw:" + kdbxPw);
        args.add("-ref-Title:" + title);
        args.add("-FailIfNoEntry");
        args.add("-Field:" + field);
        logger.debug(args
                .stream()
                .map(arg -> arg.startsWith("-pw:")
                        ? "-pw:******"
                        : arg)
                .collect(Collectors.joining(" ")));
        
        Process p = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
        String output = IOUtils.readFully(p.getInputStream());
        if (p.exitValue() != 0) {
            logger.error(output);
            throw new RuntimeException("KPScript failed, exit value " + p.exitValue());
        }
        
        String[] lines = output.split(EOL);
        if (lines.length < 2
                || !"OK: Operation completed successfully.".equals(lines[lines.length - 1])) {
            logger.error(output);
            throw new RuntimeException("KPScript failed");
        }
        return Arrays.stream(lines, 0, lines.length - 1).collect(Collectors.joining(EOL));
    }
}

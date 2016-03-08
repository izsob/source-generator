package actions;

import org.sourcegenerator.Converter;
import org.sourcegenerator.converter.FieldToMatcherConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MatcherForDataClassAction extends SourceGeneratorAction {


    @Override
    public Converter getConverter(String sourcePath) {
        return new FieldToMatcherConverter(getMatchersPath(Paths.get(sourcePath).getParent()));
    }

    private String getMatchersPath(Path deepestFolder) {
        while(! deepestFolder.getRoot().equals(deepestFolder)) {
            String matchersFilename = deepestFolder.toString() + "/customMatchers.properties";
            if (Files.exists(Paths.get(matchersFilename))) {
                return matchersFilename;
            }
            deepestFolder = deepestFolder.getParent();
        }
        return null;
    }

}

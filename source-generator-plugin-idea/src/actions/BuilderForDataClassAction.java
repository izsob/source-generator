package actions;

import org.sourcegenerator.Converter;
import org.sourcegenerator.converter.FieldToBuilderMethodConverter;

public class BuilderForDataClassAction extends SourceGeneratorAction {


    @Override
    public Converter getConverter(String sourcePath) {
        return new FieldToBuilderMethodConverter();
    }
}

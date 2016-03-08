package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.sourcegenerator.Converter;
import org.sourcegenerator.SourceGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public abstract class SourceGeneratorAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Object navigatable = anActionEvent.getData(CommonDataKeys.NAVIGATABLE);
        if (!(navigatable instanceof PsiClass)) {
            return;
        }

        PsiClass selectedClass = (PsiClass) navigatable;
        VirtualFile virtualFile = selectedClass.getContainingFile().getVirtualFile();
        String sourcePath = virtualFile.getCanonicalPath();

        try {

            Converter converter = getConverter(sourcePath);
            SourceGenerator sourceGenerator = new SourceGenerator(converter);
            String convertedJavaContent = sourceGenerator.generate(sourcePath);

            Files.write(getPath(sourcePath, converter.getName()), convertedJavaContent.getBytes(), StandardOpenOption.CREATE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Path getPath(String sourcePath, String type) {
        return Paths.get(sourcePath.substring(0, sourcePath.length()-5) + type + ".java");
    }

    public abstract Converter getConverter(String sourcePath);
}

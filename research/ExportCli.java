import com.wrath.pakviewer.WrathExports;
import java.nio.file.*;
import java.util.zip.*;
public final class ExportCli {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("ExportCli input.pak category output.zip");
        Path input=Paths.get(args[0]);byte[] data=Files.readAllBytes(input);
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(Paths.get(args[2])))){
            WrathExports.write(data,input.getFileName().toString(),args[1],zip);
        }
    }
}

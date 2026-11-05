import com.wrath.pakviewer.WrathGeometry;
import java.nio.file.*;
import java.util.zip.*;

/** Desktop harness runs the exact Android decoder without Android dependencies. */
public final class GeometryCli {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("GeometryCli input.pak output.zip");
        WrathGeometry.Result result=WrathGeometry.decode(Files.readAllBytes(Paths.get(args[0])));
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
            result.writeZip(zip);
        }
        System.out.print(result.audit());
    }
}

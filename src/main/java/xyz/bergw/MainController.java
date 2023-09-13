package xyz.bergw;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

@RestController
public class MainController {

    Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private static final String SAVE_S3_BUCKET = "bergw-xyz";
    
    private final S3Client s3;

    public MainController() {
        s3 = S3Client.builder().region(Region.EU_WEST_1).build();
    }

    @RequestMapping("/")
    void root(HttpServletResponse response) throws IOException {
        response.sendRedirect("http://static.bergw.xyz");
    }

    @RequestMapping(value = "/save", method = RequestMethod.GET)
    String getSaveUi() {
        return """
<!DOCTYPE html>
<html>
<body>

<a href='saves'>Saves</a><br><br>

<h1>Save</h1>

<form action="/save" method="post" enctype="multipart/form-data" id="save">
  <label for="name">Name:</label>
  <input type="text" id="name" name="name"><br><br>
  <label for="file">File:</label>
  <input type="file" id="file" name="file">
  <input type="submit" value="Submit">
</form><br><br>
Data:<br><br>
<textarea rows="20" cols="100" id="data" name="data" form="save">
</textarea>
<br><br>

<h1>Load</h1>

<form action="/load" method="get">
  <label for="name">Name:</label>
  <input type="text" id="name" name="name">
  <input type="submit" value="Submit">
</form>

</body>
</html>
                """;
    }

    @RequestMapping(value = "/saves", method = RequestMethod.GET)
    String getSaves(HttpServletResponse response) throws IOException {
        StringBuilder stringBuilder = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                <body>
                                
                <h1>Saves</h1><table><tr><th>Name</th><th>Size</th></tr>
                """);

        try {
            ListObjectsRequest listObjects = ListObjectsRequest.builder()
                    .bucket(SAVE_S3_BUCKET)
                    .prefix("save")
                    .build();

            ListObjectsResponse listObjectsResponse = s3.listObjects(listObjects);
            List<S3Object> objects = listObjectsResponse.contents();
            for (S3Object object : objects) {
                String shortName = object.key().substring(5);
                stringBuilder.append("<tr><td><a href='load?name=");
                stringBuilder.append(shortName);
                stringBuilder.append("'>");
                stringBuilder.append(shortName);
                stringBuilder.append("</a></td><td>");
                stringBuilder.append(object.size() / 1024);
                stringBuilder.append(" KB</td></tr>");
            }
        } catch (S3Exception e) {
            logger.error("/saves", e);
            response.sendError(500);
            return e.getMessage();
        }

        stringBuilder.append("""
</table>
</body>
</html>
                """);
        return stringBuilder.toString();
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    String putSave(
            @RequestParam(required = true) String name,
            @RequestParam(required = false) String data,
            @RequestParam(required = false) MultipartFile file,
            HttpServletResponse response
            ) throws IOException {
        if (name == null || name.isEmpty()) {
            response.sendError(400);
            return "Name is required";
        }

        if (data == null && (file == null || file.isEmpty())) {
            response.sendError(400);
            return "Data or file is required";
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(SAVE_S3_BUCKET)
                .key("save/" + name)
                .build();

        RequestBody requestBody;
        if (data != null && !data.isEmpty()) {
            requestBody = RequestBody.fromString(data);
        }
        else {
            requestBody = RequestBody.fromInputStream(file.getInputStream(), file.getSize());
        }

        s3.putObject(putObjectRequest, requestBody);
        return "OK";
    }

    @RequestMapping(value = "/load", method = RequestMethod.GET)
    String getSave(
            @RequestParam String name,
            HttpServletResponse response
    ) throws IOException {
        if (name == null || name.isEmpty()) {
            response.sendError(400);
            return "name is required";
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(SAVE_S3_BUCKET)
                .key("save/" + name)
                .build();

        ResponseInputStream<GetObjectResponse> object;
        try {
            object = s3.getObject(getObjectRequest);
        }
        catch (S3Exception e) {
            response.sendError(400);
            return name + "does not exist";
        }

//        response.sendRedirect("http://static.bergw.xyz/save/" + name);
//        return "";

        StringBuilder stringBuilder = new StringBuilder("""
<!DOCTYPE html>
<html>
<body>
""");

        if (name.endsWith(".txt")) {
            Scanner scanner = new Scanner(object);
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine());
                stringBuilder.append("<br>");
            }
        }
        else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            stringBuilder.append("<img src='http://static.bergw.xyz/save/" + name + "'>");
        }
        else if (name.endsWith(".mp4")) {
            stringBuilder.append("<video controls><source type='video/mp4' src='http://static.bergw.xyz/save/" + name + "'></video>");
        }

        stringBuilder.append("</body></html>");
        return stringBuilder.toString();
    }
}
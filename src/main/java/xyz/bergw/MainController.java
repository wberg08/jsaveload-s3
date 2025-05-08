package xyz.bergw;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@PropertySource("classpath:bergw.properties")
public class MainController {

    Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private static final String SAVE_S3_BUCKET = "bergw-xyz";
    
    private final S3Client s3;
    private final Pattern secretGroup;

    @Value("${save.password}")
    private String savePassword = null;

    public MainController() {
        s3 = S3Client.builder().region(Region.EU_WEST_1).build();
        secretGroup = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", Pattern.CASE_INSENSITIVE);
    }

    @RequestMapping("/healthcheck")
    void healthcheck(HttpServletResponse response) throws IOException {}

    @RequestMapping("/")
    void root(HttpServletResponse response) throws IOException {

        found(response, "https://www.bergw.xyz/");
    }

    @RequestMapping("/mail")
    void mail(HttpServletResponse response) throws IOException {
        found(response, "https://bergw-xyz.awsapps.com/mail");
    }

    @RequestMapping("/blog")
    void blog(HttpServletResponse response) throws IOException {
        found(response, "https://blog.bergw.xyz/");
    }

    @RequestMapping(value = "/save", method = RequestMethod.GET)
    String getSaveUi() {
        return """
<!DOCTYPE html>
<html>

<head>
<link rel="stylesheet" href="https://www.bergw.xyz/res/savestyle.css">
</head>
<body>

<a href='saves/save/'>Saves</a><br><br>

<h1>Save</h1>

<form action="save" method="post" enctype="multipart/form-data" id="save">
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

<form action="load" method="get">
  <label for="name">Name:</label>
  <input type="text" id="name" name="name">
  <input type="submit" value="Submit">
</form>

</body>
</html>
                """;
    }

    @RequestMapping(value = "/saves/save/", method = RequestMethod.GET)
    String getSaves(@CookieValue(name = "password", required = false) String password, HttpServletResponse response) throws IOException {
        return getSaves("", password, response);
    }

    @RequestMapping(value = "/saves/save/{path}", method = RequestMethod.GET)
    String getSaves(@PathVariable(value="path") String path,
                    @CookieValue(name = "password", required = false) String password,
                    HttpServletResponse response) throws IOException {
        boolean authenticated = false;
        if (savePassword != null && password != null && password.equals(savePassword)) {
            authenticated = true;
        }

        StringBuilder stringBuilder = new StringBuilder("""
                <!DOCTYPE html>
                <html>
        <head>
                  <link rel="shortcut icon" href="https://www.bergw.xyz/res/savefavicon.png">
          <link rel="stylesheet" href="https://www.bergw.xyz/res/savestyle.css">
        </head>
                <body>
                <script type="text/javascript">
                var password = "";
                const cookies = document.cookie.split(';');
                for (let cookie of cookies) {
                  cookie = cookie.trim();
                  if (cookie.startsWith("password=")) {
                    password = cookie.substring(9);
                  }
                }
                function deleteF(name) {
                    let uiDiv = document.createElement("div");
                    myDelete = document.getElementById("delete" + name);
                    button = document.getElementById(name + "-delete-button");
    
                    const xhr = new XMLHttpRequest();
                    xhr.open("POST", "/delete?name=" + name + "&password=" + password, true);
                    xhr.onload = (e) => {
                      if (xhr.readyState === 4) {
                        if (xhr.status === 200) {
                          uiDiv.innerHTML = "Deleted"
                          button.remove();
                        } else {
                          uiDiv.innerHTML = "Delete failed"
                        }
                      }
                    };
                    xhr.onerror = (e) => {
                      uiDiv.innerHTML = "Delete really failed"
                    };
                    xhr.send(null);
    
                    myDelete.appendChild(uiDiv);
                }

                function archiveF(name) {
                    let uiDiv = document.createElement("div");
                    myArchive = document.getElementById("archive" + name);
                    button = document.getElementById(name + "-archive-button");
    
                    const xhr = new XMLHttpRequest();
                    xhr.open("POST", "/archive?name=" + name, true);
                    xhr.onload = (e) => {
                      if (xhr.readyState === 4) {
                        if (xhr.status === 200) {
                          uiDiv.innerHTML = "Archived"
                          button.remove();
                        } else {
                          uiDiv.innerHTML = "Archive failed"
                        }
                      }
                    };
                    xhr.onerror = (e) => {
                      uiDiv.innerHTML = "Archive really failed"
                    };
                    xhr.send(null);
    
                    myArchive.appendChild(uiDiv);
                }
                </script>

<a href='/save'>Save</a><br><br>
                """);

        try {
            if ("".equals(path)) {
                ListObjectsRequest lo = ListObjectsRequest.builder()
                        .bucket(SAVE_S3_BUCKET)
                        .prefix("save/")
                        .delimiter("/")
                        .build();
                ListObjectsResponse lor = s3.listObjects(lo);

                stringBuilder.append(
                        "<p><h1>groups</h1></p>");

                for (CommonPrefix commonPrefix : lor.commonPrefixes()) {
                    String subpath = commonPrefix.prefix().substring(5, commonPrefix.prefix().length() - 1);
                    Matcher matcher = secretGroup.matcher(subpath);
                    if (!matcher.find() || authenticated) {
                        stringBuilder.append("<h2><a href='/saves/save/" + subpath + "'>" + subpath + "</a></h2> ");
                    }
                }
            }

            logger.info("SAVE_S3_BUCKET {}", SAVE_S3_BUCKET);
            logger.info("prefix {}","save/" + path + "/");

            String prefix;
            if (path.isEmpty()) {
                prefix = "save/";
            } else {
                prefix = "save/" + path + "/";
            }

            ListObjectsRequest listObjects = ListObjectsRequest.builder()
                    .bucket(SAVE_S3_BUCKET)
                    .prefix(prefix)
                    .build();
            ListObjectsResponse listObjectsResponse = s3.listObjects(listObjects);

            stringBuilder.append(
                    "<p><h1>Saves at " + ("".equals(path) ? "/" : path) + "</h1></p><table><tr><th>Name</th><th>Size</th><th></th></tr>");

            List<S3Object> objects = listObjectsResponse.contents();
            for (S3Object object : objects) {
                String shortName = object.key();
                int lastSlashIndex = shortName.lastIndexOf('/');
                if ("".equals(path) && lastSlashIndex != 4) {
                    continue;
                }
                shortName = shortName.substring(lastSlashIndex + 1);

                String html = String.format("""
        <tr>
          <td><a href='/load?name=%s'>%s</a></td>
          <td>%d KB</td>
          <td><button onclick="location.href='/editText?name=%s'" type="button">Edit</button></td>
          <td><div id='archive%s'><button id='%s-archive-button' onclick=\"archiveF('%s')\">Archive</button></div></td>
          <td><div id='delete%s'><button id='%s-delete-button' onclick=\"deleteF('%s')\">Delete</button></div></td>
        </tr>
            """,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        shortName,
                        object.size() / 1024,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        path + ("".equals(path) ? "" : "/" ) + shortName,
                        path + ("".equals(path) ? "" : "/" ) + shortName);
                stringBuilder.append(html);
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
            @RequestParam String name,
            @RequestParam(required = false) String data,
            @RequestParam(required = false) MultipartFile file,
            @CookieValue(name = "password") String password,
            HttpServletResponse response
            ) throws IOException {
        if (savePassword != null && password != null && password.equals(savePassword)) {
            return "401 password cookie did not match";
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

    /**
     * Appends the prefix 'save/' to avoid deleting anything else in the bucket.
     */
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    String deleteSave(
            @RequestParam String name,
            @RequestParam String password,
            HttpServletResponse response
    ) throws IOException {
        logger.info("delete name = " + name);

        if (savePassword != null && password != null && password.equals(savePassword)) {
            return "401 password cookie did not match";
        }

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(SAVE_S3_BUCKET)
                .key("save/" + name)
                .build();
        try {
            s3.deleteObject(deleteObjectRequest);
        }
        catch (S3Exception e) {
            logger.info("",e);
            response.sendError(400);
            return name + "does not exist";
        }

        return "OK";
    }

    /**
     * Appends the prefix 'save/' to avoid archiving anything else in the bucket.
     */
    @RequestMapping(value = "/archive", method = RequestMethod.POST)
    String archiveSave(
            @RequestParam String name,
            @RequestParam String password,
            HttpServletResponse response
    ) throws IOException {
        logger.info("archive name = " + name);

        if (savePassword != null && password != null && password.equals(savePassword)) {
            return "401 password cookie did not match";
        }

        String objectName = name.substring(name.lastIndexOf("/") + 1);

        CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                .sourceBucket(SAVE_S3_BUCKET)
                .sourceKey("save/" + name)
                .destinationBucket(SAVE_S3_BUCKET)
                .destinationKey("save/archive/" + objectName)
                .build();

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(SAVE_S3_BUCKET)
                .key("save/" + name)
                .build();
        try {
            s3.copyObject(copyObjectRequest);
            s3.deleteObject(deleteObjectRequest);
        }
        catch (S3Exception e) {
            logger.info("",e);
            response.sendError(400);
            return name + "does not exist";
        }

        return "OK";
    }

    @RequestMapping(value = "/load", method = RequestMethod.GET)
    String getSave(
            @RequestParam(required = true) String name,
            HttpServletResponse response
    ) throws IOException {
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

        StringBuilder stringBuilder = new StringBuilder("""
<!DOCTYPE html>
<html>

<head>
<link rel="stylesheet" href="https://www.bergw.xyz/res/savestyle.css">
</head>
<body>
""");

        if (name.endsWith(".txt")) {
            Scanner scanner = new Scanner(object);
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine());
                stringBuilder.append("<br>");
            }
        }
        else if (name.endsWith(".png") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".gif")) {
            stringBuilder.append("<img src='https://www.bergw.xyz/save/").append(name).append("'>");
        }
        else if (name.endsWith(".mp4")) {
            stringBuilder.append("<video controls><source type='video/mp4' src='https://www.bergw.xyz/save/").append(name).append("'></video>");
        }
        else if (name.endsWith(".mp3")) {
            stringBuilder.append("<audio controls><source type='audio/mpeg' src='https://www.bergw.xyz/save/").append(name).append("'></video>");
        }
        else {
            response.sendRedirect("https://www.bergw.xyz/save/" + name);
            return "";
        }

        stringBuilder.append("</body></html>");
        return stringBuilder.toString();
    }

    @RequestMapping(value = "/editText", method = RequestMethod.GET)
    String editText(
            @RequestParam(required = true) String name,
            HttpServletResponse response
    ) throws IOException {
        if (!name.endsWith(".txt")) {
            response.sendRedirect("https://www.bergw.xyz/save/" + name);
            return "";
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(SAVE_S3_BUCKET)
                .key("save/" + name)
                .build();

        if (!name.endsWith(".txt")) {
            response.sendRedirect("https://www.bergw.xyz/save/" + name);
            return "";
        }

        ResponseInputStream<GetObjectResponse> object;
        try {
            object = s3.getObject(getObjectRequest);
        } catch (S3Exception e) {
            response.sendError(400);
            return name + "does not exist";
        }

        Scanner scanner = new Scanner(object);
        StringBuilder textFile = new StringBuilder();
        while (scanner.hasNextLine()) {
            textFile.append(scanner.nextLine());
            textFile.append("&#13;&#10;");
        }

        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                  <title>Edit: %s</title>
                  <style type="text/css">
                  body {
                    margin: 10px;
                    background-color: #000;
                  }
                  * {
                    margin: 10px;
                  }
                  h1 {
                    color: #555;
                  }
                  textarea {
                    background-color: #111;
                    color: #999;
                    border-style: ridge;
                    border-width: 10px;
                    padding: 10px;
                    -webkit-box-sizing: border-box;
                    -moz-box-sizing: border-box;
                    box-sizing: border-box;
                    width: 98%%;
                  }
                  </style>
                  <meta content="text/html;charset=utf-8" http-equiv="Content-Type">
                  <meta content="utf-8" http-equiv="encoding">
                  <link rel="shortcut icon" href="https://www.bergw.xyz/res/savefavicon.png">
                
                  <script>
                    function scrollToBottom() {
                        var textarea = document.getElementById('data');
                        textarea.scrollTop = textarea.scrollHeight;
                    }
                  </script>
                </head>
                <body>
                <h1>Edit: %s</h1>
                <textarea rows="20" cols="100" id="data" name="data" form="save">%s</textarea>
                <form action="save" method="post" enctype="multipart/form-data" id="save">
                  <input type="hidden" id="name" name="name" value="%s">
                  <input type="submit" value="Submit">
                </form>
                <button onclick="scrollToBottom()">Scroll to bottom</button>
                </body>
                </html>
                """, name, name, textFile, name);
    }

    /**
     * Used to set the password as a browser cookie.
     * Enabling the annotation adds the method to the API and makes it insecure.
     */
//    @RequestMapping(value = "/setPassword", method = RequestMethod.GET)
    void setPassword(HttpServletResponse response) throws IOException {
        Cookie cookie = new Cookie("password", savePassword);
        cookie.setMaxAge(60*60*24*365);
        response.addCookie(cookie);
    }

    /**
     * sendRedirect sets 302
     */
    private static void found(HttpServletResponse response, String url) throws IOException {
        response.setStatus(301);
        response.setHeader("Location", url);
        response.setHeader("Connection", "close");
        response.flushBuffer();
    }
}

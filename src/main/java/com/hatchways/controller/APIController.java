package com.hatchways.controller;

import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;

import java.io.InputStreamReader;
import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.hatchways.exception.InvalidSortByException;
import com.hatchways.exception.InvalidDirectionException;
import com.hatchways.exception.InvalidTagsException;

@RestController
public class APIController {

    // GET API route that allows a ping to the server
    @RequestMapping("/api/ping")
    @GetMapping
    public Map<String, Boolean> PingServer() {
        HashMap<String, Boolean> map = new HashMap<>();
        String url = "https://hatchways.io/api/assessment/blog/posts" + "?tag=\"\"";

        try {
            if (getStatus(url) == 200) {
                map.put("success", true);
            } else {
                map.put("success", false);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    // API route that fetches posts according to user specified parameters
    @RequestMapping("/api/posts")
    @GetMapping
    @Cacheable("tag")
    public Map<String, TreeSet<Post>> GetPosts(@RequestParam String tags, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "asc") String direction) {


        if (tags == null || tags.length() == 0) {
            throw new InvalidTagsException();
        }
        if (!isValidDirection(direction)) {
            throw new InvalidDirectionException();
        }

        if (!isValidSortBy(sortBy)) {
            throw new InvalidSortByException();
        }

        String url = "https://hatchways.io/api/assessment/blog/posts";
        String[] Tags = tags.split(",");
        // A comparator object is required when dealing with TreeSets of class objects
        PostComparator postComparator = new PostComparator();
        postComparator.setSortBy(sortBy);
        postComparator.setDirection(direction);
        TreeSet<Post> responseSet = new TreeSet<>(postComparator);
        // A list to keep track of all concurrent calls
        List<CompletableFuture<TreeSet<Post>>> completableFutures = new ArrayList<>();
        // This loop is where concurrent calls to API are made
        for (String tag : Tags) {
            CompletableFuture<TreeSet<Post>> requestCompletableFuture = makeConcurrentCall(responseSet,tag,sortBy,direction);
            completableFutures.add(requestCompletableFuture);
        }
        // Wait for all calls to finish
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[completableFutures.size()])).join();
        HashMap<String, TreeSet<Post>> map = new HashMap<>();
        map.put("posts", responseSet);
        return map;
    }

    // This function implements concurrency in making hatchways API calls
    @Async
    public CompletableFuture<TreeSet<Post>> makeConcurrentCall(TreeSet<Post> responseSet, String tag, String sortBy, String direction) {
        String url = "https://hatchways.io/api/assessment/blog/posts";
        // since responseSet is a shared resource, a mutex lock is required when modifying it
        Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire();
            try {
                responseSet.addAll(getResponse(tag, sortBy, direction));
            } catch (IOException e) {}
            semaphore.release();
        } catch (InterruptedException e) {}
        return CompletableFuture.completedFuture(responseSet);
    }

    // helper function to get the response code of the API endpoint
    public static Integer getStatus(String url) throws IOException {
            URL siteURL = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
            return connection.getResponseCode();
    }

    // helper function to populate the Post object and making the call to hatchways API
    public static TreeSet<Post> getResponse(String tag, String sortBy, String direction) throws IOException {
        String url = "https://hatchways.io/api/assessment/blog/posts?tag=" + tag;
        PostComparator postComparator = new PostComparator();
        postComparator.setSortBy(sortBy);
        postComparator.setDirection(direction);
        TreeSet<Post> response = new TreeSet<>(postComparator);
        URL siteURL = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
        int responseCode = connection.getResponseCode();
        InputStream inputStream;
        if (200 <= responseCode && responseCode <= 299) {
            inputStream = connection.getInputStream();
        } else {
            inputStream = connection.getErrorStream();
        }

        // get input stream as Json object
        JsonObject json = new JsonParser().parse(new InputStreamReader(inputStream)).getAsJsonObject();

        // iterate over each Post
        for (JsonElement elem : json.get("posts").getAsJsonArray()) {
            // populate post object and add to TreeSet
            Post post = new Gson().fromJson(elem.getAsJsonObject(), Post.class);
            response.add(post);
        }
        return response;
    }

    // helper function to check validity of optional parameter sortBy
    public boolean isValidSortBy(String sortBy) {
        if (sortBy == null) {
            return false;
        }
        if (sortBy.equals("id") || sortBy.equals("reads") || sortBy.equals("likes") || sortBy.equals("popularity")) {
            return true;
        }
        return false;
    }

    // helper function to check validity of optional parameter direction
    public boolean isValidDirection(String direction) {

        if (direction == null) {
            return false;
        }
        if (direction.equals("asc") || direction.equals("desc")){
            return true;
        }
        return false;
    }

    // this exception is thrown by Spring framework when a required parameter is missing in request
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParams(MissingServletRequestParameterException ex) {
        HashMap<String, String> map = new HashMap<>();
        map.put("error","Tags parameter is required");
        return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
    }

    // this is a custom exception to handle invalid sortBy inputs.
    @ExceptionHandler(value = InvalidSortByException.class)
    public ResponseEntity<Object> SortByException(InvalidSortByException ex) {
        HashMap<String, String> map = new HashMap<>();
        map.put("error","sortBy parameter is invalid");
        return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
    }

    // this is a custom exception to handle invalid direction inputs.
    @ExceptionHandler(value = InvalidDirectionException.class)
    public ResponseEntity<Object> DirectionException(InvalidDirectionException ex) {
        HashMap<String, String> map = new HashMap<>();
        map.put("error","direction parameter is invalid");
        return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
    }

    // this is a custom exception to handle invalid tags inputs.
    @ExceptionHandler(value = InvalidTagsException.class)
    public ResponseEntity<Object> InvalidTagsException(InvalidTagsException ex) {
        HashMap<String, String> map = new HashMap<>();
        map.put("error","Tags parameter is required");
        return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
    }

}

// Comparator class for TreeSet
class PostComparator implements Comparator<Post> {

    String sortBy;
    String direction;

    public int compare(Post o1, Post o2) {
        if (o1.getId() == o2.getId()) {
            return 0;
        }
        switch(sortBy) {
            case "id":
                if (direction.equals("asc")){
                    return o1.getId() > o2.getId() ? 1 : -1;
                }
                else {
                    return o1.getId() < o2.getId() ? 1 : -1;
                }
            case "reads":
                if (direction.equals("asc")){
                    return o1.getReads() > o2.getReads() ? 1 : -1;
                }
                else {
                    return o1.getReads() < o2.getReads() ? 1 : -1;
                }
            case "likes":
                if (direction.equals("asc")){
                    return o1.getLikes() > o2.getLikes() ? 1 : -1;
                }
                else {
                    return o1.getLikes() < o2.getLikes() ? 1 : -1;
                }
            case "popularity":
                if (direction.equals("asc")){
                    return o1.getPopularity() > o2.getPopularity() ? 1 : -1;
                }
                else {
                    return o1.getPopularity() < o2.getPopularity() ? 1 : -1;
                }
            default:
                return 0;
        }
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
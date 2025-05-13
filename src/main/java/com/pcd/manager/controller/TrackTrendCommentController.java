
import com.pcd.manager.service.TrackTrendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequestMapping("/api")
public class TrackTrendCommentController {

    private final TrackTrendService trackTrendService;

    @Autowired
    public TrackTrendCommentController(TrackTrendService trackTrendService) {
        this.trackTrendService = trackTrendService;
    }

    // Approach 1: Standard controller method with explicit request method
    @RequestMapping(value = "/tracktrend/{id}/add-comment", method = RequestMethod.POST)
    public String addCommentMethod1(
            @PathVariable("id") Long trackTrendId,
            @RequestParam("content") String content,
            Authentication authentication) {
        
        if (authentication == null) {
            throw new IllegalStateException("User must be authenticated to add comments");
        }
        
        String userEmail = authentication.getName();
        trackTrendService.addComment(trackTrendId, content, userEmail);
        
        return "redirect:/tracktrend/" + trackTrendId;
    }
    
    @RequestMapping(value = "/tracktrend/{id}/add-comment", method = RequestMethod.GET)
    public String getAddCommentMethod1(@PathVariable("id") Long trackTrendId) {
        return "redirect:/tracktrend/" + trackTrendId;
    }
    
    // Approach 2: PostMapping with explicit consumes/produces
    @PostMapping(
        value = "/tracktrend-comments/add/{trackTrendId}",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public String addCommentMethod2(
            @PathVariable("trackTrendId") Long trackTrendId,
            @RequestParam("content") String content,
            Authentication authentication) {
        
        if (authentication == null) {
            throw new IllegalStateException("User must be authenticated to add comments");
        }
        
        String userEmail = authentication.getName();
        trackTrendService.addComment(trackTrendId, content, userEmail);
        
        return "redirect:/tracktrend/" + trackTrendId;
    }
    
    @GetMapping("/tracktrend-comments/add/{trackTrendId}")
    public String getAddCommentMethod2(@PathVariable("trackTrendId") Long trackTrendId) {
        return "redirect:/tracktrend/" + trackTrendId;
    }
    
    // Approach 3: ResponseBody with AJAX-style response
    @PostMapping("/tracktrend/{id}/comment-ajax")
    @ResponseBody
    public String addCommentAjax(
            @PathVariable("id") Long trackTrendId,
            @RequestParam("content") String content,
            Authentication authentication) {
        
        if (authentication == null) {
            return "error:authentication_required";
        }
        
        String userEmail = authentication.getName();
        trackTrendService.addComment(trackTrendId, content, userEmail);
        
        return "success";
    }
    
    @GetMapping("/tracktrend/{id}/comment-ajax")
    @ResponseBody
    public String getAddCommentAjax(@PathVariable("id") Long trackTrendId) {
        return "Please use POST method to add comments";
    }
} 
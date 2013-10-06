
import java.util.HashMap;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

/**
 *
 * @author mmb
 */
@ApplicationScoped
@Named(value="service")
public class Service {
    
    private Map<String, User> users;
    
    public Service(){
        users = new HashMap<>();
        User u1 = new User("Liv", "liv123",0, "admin", "1209137463");
        User u2 = new User("Julie", "julie123",1, "user", "1205137463");
        User u3 = new User("Nick", "nick123",2, "admin", "2407137463");
        User u4 = new User("Mark", "mark123",3, "user", "2308672342");
        
        users.put("Liv", u1);
        users.put("Julie", u2);
        users.put("Nick", u3);
        users.put("Mark", u4);
        
    }
    
    public boolean containsUser(String name, String pass){
        boolean result = false;
        User temp = users.get(name);
        if(temp != null){
            if(temp.getPassword().equals(pass)){
                result = true;
            }
        }
        return result;
    }
    
    public void addUser(User u){
        users.put(u.getName(), u);
    }
    
    public Map<String,User> getUsers(){
        return new HashMap<>(users);
    }
    
    public boolean isAdmin(String name){
        boolean result = false;
        if(users.get(name).getUserPri().equals("admin")){
            result = true;
        }
        return result;
    }
}

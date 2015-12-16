/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import java.util.Deque;

/**
 *
 * @author User
 */
public interface PathfindingCompleteListener {
    
    public void foundPath(Deque<AIFloat3> path);
}

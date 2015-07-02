/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.neural;

import org.neuroph.core.Weight;

/**
 *
 * @author User
 */
public class MultiuserWeight extends Weight{
    
    int users;
    
    public MultiuserWeight(int users){
        this.users = users;
        
    }
    
    
    public int getUsers(){
        return users;
    }
    
    public void setUsers(int u){
        users = u;
    }
}

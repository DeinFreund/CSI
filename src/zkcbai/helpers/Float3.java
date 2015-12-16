/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;

/**
 *
 * @author User
 */
public class Float3 {

    float x, y, z;

    public Float3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Float3(Float3 a) {
        this(a.x, a.y, a.z);
    }
    
    public Float3(AIFloat3 a){
        this(a.x, a.y, a.z);
    }
}

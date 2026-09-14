package com.stupidbeauty.sisterfuture.memory;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import com.stupidbeauty.sisterfuture.bean.MyObjectBox;
import io.objectbox.BoxStore;
import io.objectbox.Box;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import net.tatans.tensorflowtts.module.FastSpeech2;
import net.tatans.tensorflowtts.module.MBMelGan;

/** Run on an ARM phone: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4.class)
public class OfflineMemoryTest {
    private Context getContext() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    @Test public void testRealChineseEmbeddings() throws Exception {
        try(BgeEmbeddingModel model=new BgeEmbeddingModel(getContext().getAssets())) {
            float[] a=model.embed("我喜欢喝咖啡"), b=model.embed("我爱喝咖啡"), c=model.embed("服务器的登录密码");
            assertEquals(512,a.length);
            assertEquals(1.0,dot(a,a),0.0001);
            assertTrue(dot(a,b)>dot(a,c)+0.3);
        }
    }
    @Test public void testRealBackfillAndReopen() throws Exception {
        File dir=new File(getContext().getCacheDir(),"embedding-test-"+System.nanoTime());
        long id;
        try(BoxStore store=MyObjectBox.builder().directory(dir).build()) {
            Box<MemoryEntity> box=store.boxFor(MemoryEntity.class);
            MemoryEntity memory=new MemoryEntity(); memory.setContent("我喜欢喝咖啡");
            id=box.put(memory);
            try(MemoryEmbeddingIndexer indexer=new MemoryEmbeddingIndexer(store,getContext().getAssets())) {
                indexer.request();
                long deadline=System.nanoTime()+120000000000L;
                while(!MemoryEmbeddingIndexer.isCurrent(box.get(id)) && System.nanoTime()<deadline) Thread.sleep(50);
                assertTrue(MemoryEmbeddingIndexer.isCurrent(box.get(id)));
            }
        }
        try(BoxStore store=MyObjectBox.builder().directory(dir).build()) {
            MemoryEntity restored=store.boxFor(MemoryEntity.class).get(id);
            assertTrue(MemoryEmbeddingIndexer.isCurrent(restored));
            assertEquals(1.0,dot(restored.getEmbedding(),restored.getEmbedding()),0.0001);
            SemanticMemorySearch.Result result=new SemanticMemorySearch(getContext().getAssets()).search(
                    store.boxFor(MemoryEntity.class).getAll(),"我平时爱喝什么饮品",5,0.5);
            assertNull(result.fallbackReason);
            assertEquals(1,result.matches.size());
            assertTrue(result.matches.get(0).semantic);
            assertFalse(result.matches.get(0).keyword);
            assertEquals(id,result.matches.get(0).memory.getId());
        } finally { BoxStore.deleteAllFiles(dir); }
    }
    @Test public void testExistingSpeechPipeline() throws Exception {
        File fast=copyAsset("fastspeech2_quan.tflite"), mel=copyAsset("mb_melgan_new.tflite");
        // n i3 h ao3 #3 (the bundled Baker vocabulary).
        float[] audio=new MBMelGan(mel.getPath()).getAudio(new FastSpeech2(fast.getPath())
                .getMelSpectrogram(new int[]{18, 80, 13, 50, 5},1f));
        assertTrue(audio.length>0);
        for(float sample:audio) assertTrue(Float.isFinite(sample));
    }
    private File copyAsset(String name) throws Exception {
        File output=new File(getContext().getCacheDir(),"test-"+name);
        try(InputStream in=getContext().getAssets().open(name); FileOutputStream out=new FileOutputStream(output)) {
            byte[] buffer=new byte[8192]; int n;
            while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
        }
        return output;
    }
    private static double dot(float[] a,float[] b) {
        double result=0; for(int i=0;i<a.length;i++) result+=(double)a[i]*b[i]; return result;
    }
}

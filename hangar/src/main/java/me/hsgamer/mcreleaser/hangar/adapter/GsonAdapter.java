package me.hsgamer.mcreleaser.hangar.adapter;

import com.github.mizosoft.methanol.adapter.ForwardingDecoder;
import com.github.mizosoft.methanol.adapter.ForwardingEncoder;
import com.github.mizosoft.methanol.adapter.gson.GsonAdapterFactory;
import com.google.gson.Gson;

public class GsonAdapter {
    public static final Gson INSTANCE = new Gson();

    public static class Encoder extends ForwardingEncoder {
        public Encoder() {
            super(GsonAdapterFactory.createEncoder(INSTANCE));
        }
    }

    public static class Decoder extends ForwardingDecoder {
        public Decoder() {
            super(GsonAdapterFactory.createDecoder(INSTANCE));
        }
    }
}
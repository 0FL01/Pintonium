package org.taumc.celeritas.api.options.structure;

import org.taumc.celeritas.api.options.OptionIdentifier;

public final class StandardOptions {
    public static class Group {
        public static final OptionIdentifier<Void> RENDERING = OptionIdentifier.create("minecraft", "rendering");
        public static final OptionIdentifier<Void> WINDOW = OptionIdentifier.create("minecraft", "window");
        public static final OptionIdentifier<Void> INDICATORS = OptionIdentifier.create("minecraft", "indicators");
        public static final OptionIdentifier<Void> GRAPHICS = OptionIdentifier.create("minecraft", "graphics");
        public static final OptionIdentifier<Void> MIPMAPS = OptionIdentifier.create("minecraft", "mipmaps");
        public static final OptionIdentifier<Void> DETAILS = OptionIdentifier.create("minecraft", "details");
        public static final OptionIdentifier<Void> CHUNK_UPDATES = OptionIdentifier.create("celeritas", "chunk_updates");
        public static final OptionIdentifier<Void> RENDERING_CULLING = OptionIdentifier.create("celeritas", "rendering_culling");
        public static final OptionIdentifier<Void> CPU_SAVING = OptionIdentifier.create("celeritas", "cpu_saving");
        public static final OptionIdentifier<Void> SORTING = OptionIdentifier.create("celeritas", "sorting");
        public static final OptionIdentifier<Void> LIGHTING = OptionIdentifier.create("celeritas", "lighting");
    }

    public static class Pages {
        public static final OptionIdentifier<Void> GENERAL = OptionIdentifier.create("celeritas", "general");
        public static final OptionIdentifier<Void> QUALITY = OptionIdentifier.create("celeritas", "quality");
        public static final OptionIdentifier<Void> PERFORMANCE = OptionIdentifier.create("celeritas", "performance");
        public static final OptionIdentifier<Void> ADVANCED = OptionIdentifier.create("celeritas", "advanced");
        public static final OptionIdentifier<Void> SHADERS = OptionIdentifier.create("celeritas", "shaders");
    }

    public static class Option {
        public static final OptionIdentifier<?> RENDER_DISTANCE = OptionIdentifier.create("minecraft", "render_distance");
        public static final OptionIdentifier<?> SIMULATION_DISTANCE = OptionIdentifier.create("minecraft", "simulation_distance");
        public static final OptionIdentifier<?> BRIGHTNESS = OptionIdentifier.create("minecraft", "brightness");
        public static final OptionIdentifier<?> GUI_SCALE = OptionIdentifier.create("minecraft", "gui_scale");
        public static final OptionIdentifier<?> FULLSCREEN = OptionIdentifier.create("minecraft", "fullscreen");
        public static final OptionIdentifier<?> FULLSCREEN_RESOLUTION = OptionIdentifier.create("minecraft", "fullscreen_resolution");
        public static final OptionIdentifier<?> VSYNC = OptionIdentifier.create("minecraft", "vsync");
        public static final OptionIdentifier<?> MAX_FRAMERATE = OptionIdentifier.create("minecraft", "max_frame_rate");
        public static final OptionIdentifier<?> VIEW_BOBBING = OptionIdentifier.create("minecraft", "view_bobbing");
        public static final OptionIdentifier<?> INACTIVITY_FPS_LIMIT = OptionIdentifier.create("minecraft", "inactivity_fps_limit");
        public static final OptionIdentifier<?> ATTACK_INDICATOR = OptionIdentifier.create("minecraft", "attack_indicator");
        public static final OptionIdentifier<?> AUTOSAVE_INDICATOR = OptionIdentifier.create("minecraft", "autosave_indicator");
        public static final OptionIdentifier<?> GRAPHICS_MODE = OptionIdentifier.create("minecraft", "graphics_mode");
        public static final OptionIdentifier<?> CLOUDS = OptionIdentifier.create("minecraft", "clouds");
        public static final OptionIdentifier<?> WEATHER = OptionIdentifier.create("minecraft", "weather");
        public static final OptionIdentifier<?> LEAVES = OptionIdentifier.create("minecraft", "leaves");
        public static final OptionIdentifier<?> PARTICLES = OptionIdentifier.create("minecraft", "particles");
        public static final OptionIdentifier<?> SMOOTH_LIGHT = OptionIdentifier.create("minecraft", "smooth_lighting");
        public static final OptionIdentifier<?> BIOME_BLEND = OptionIdentifier.create("minecraft", "biome_blend");
        public static final OptionIdentifier<?> ENTITY_DISTANCE = OptionIdentifier.create("minecraft", "entity_distance");
        public static final OptionIdentifier<?> ENTITY_SHADOWS = OptionIdentifier.create("minecraft", "entity_shadows");
        public static final OptionIdentifier<?> VIGNETTE = OptionIdentifier.create("minecraft", "vignette");
        public static final OptionIdentifier<?> MIPMAP_LEVEL = OptionIdentifier.create("minecraft", "mipmap_levels");
        public static final OptionIdentifier<?> CHUNK_UPDATE_THREADS = OptionIdentifier.create("celeritas", "chunk_update_threads");
        public static final OptionIdentifier<?> DEFFER_CHUNK_UPDATES = OptionIdentifier.create("celeritas", "defer_chunk_updates");
        public static final OptionIdentifier<?> BLOCK_FACE_CULLING = OptionIdentifier.create("celeritas", "block_face_culling");
        public static final OptionIdentifier<?> COMPACT_VERTEX_FORMAT = OptionIdentifier.create("celeritas", "compact_vertex_format");
        public static final OptionIdentifier<?> FOG_OCCLUSION = OptionIdentifier.create("celeritas", "fog_occlusion");
        public static final OptionIdentifier<?> ENTITY_CULLING = OptionIdentifier.create("celeritas", "entity_culling");
        public static final OptionIdentifier<?> ANIMATE_VISIBLE_TEXTURES = OptionIdentifier.create("celeritas", "animate_only_visible_textures");
        public static final OptionIdentifier<?> NO_ERROR_CONTEXT = OptionIdentifier.create("celeritas", "no_error_context");
        public static final OptionIdentifier<?> PERSISTENT_MAPPING = OptionIdentifier.create("celeritas", "persistent_mapping");
        public static final OptionIdentifier<?> CPU_FRAMES_AHEAD = OptionIdentifier.create("celeritas", "cpu_render_ahead_limit");
        public static final OptionIdentifier<?> TRANSLUCENT_FACE_SORTING = OptionIdentifier.create("celeritas", "translucent_face_sorting");
        public static final OptionIdentifier<?> USE_QUAD_NORMALS_FOR_LIGHTING = OptionIdentifier.create("celeritas", "use_quad_normals_for_lighting");
        public static final OptionIdentifier<?> RENDER_PASS_OPTIMIZATION = OptionIdentifier.create("celeritas", "render_pass_optimization");
        public static final OptionIdentifier<?> RENDER_PASS_CONSOLIDATION = OptionIdentifier.create("celeritas", "render_pass_consolidation");
        public static final OptionIdentifier<?> USE_FASTER_CLOUDS = OptionIdentifier.create("celeritas", "use_faster_clouds");
        public static final OptionIdentifier<?> ASYNC_GRAPH_SEARCH = OptionIdentifier.create("celeritas", "async_graph_search");
    }
}

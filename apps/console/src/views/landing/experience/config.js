// 资源与路径配置(对应原站 window.template_url / window.assets)
export const TEMPLATE_URL = '/landing';

export const ASSETS = [
    {
        name: 'base',
        data: {},
        items: [
            { rel: 'rel-1', name: 'scene', source: TEMPLATE_URL + '/static/model/scene.glb', type: 'glb' },
            { name: 'p1', source: TEMPLATE_URL + '/static/images/particles.png', type: 'texture' },
            { name: 'p2', source: TEMPLATE_URL + '/static/images/particles-2.png', type: 'texture' },
            { name: 'p3', source: TEMPLATE_URL + '/static/images/particles-3.png', type: 'texture' },
            { name: 'fogTexture', source: TEMPLATE_URL + '/static/images/cloud.png', type: 'texture' },
            { name: 'visionSound', source: TEMPLATE_URL + '/static/audio/combo-3/1-vision.mp3', type: 'audio' },
            { name: 'craftSound', source: TEMPLATE_URL + '/static/audio/combo-3/2-craft.mp3', type: 'audio' },
            { name: 'experienceSound', source: TEMPLATE_URL + '/static/audio/combo-3/3-experience.mp3', type: 'audio' },
            { name: 'hoverSound', source: TEMPLATE_URL + '/static/audio/sfx/hover-beep-select.mp3', type: 'audio' },
        ],
    },
];

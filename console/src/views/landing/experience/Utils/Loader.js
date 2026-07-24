import * as THREE from 'three';
import EventEmitter from './EventEmitter.js'
import { TEMPLATE_URL } from '../config.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { FBXLoader } from 'three/examples/jsm/loaders/FBXLoader.js'
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js'
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader.js'
import { FontLoader } from 'three/examples/jsm/loaders/FontLoader.js';
import { EXRLoader } from 'three/examples/jsm/loaders/EXRLoader.js'
import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js';



export default class Loader extends EventEmitter
{
    /**
     * Constructor
     */
    constructor()
    {
        super()

        this.audioContext = null

        this.setLoaders()

        this.toLoad = 0
        this.loaded = 0
        this.items = {}
    }

    /**
     * Set loaders
     */
    setLoaders()
    {
        this.loaders = []

        // Images
        this.loaders.push({
            extensions: ['jpg', 'png'],
            action: (_resource) =>
            {
                const image = new Image()

                image.addEventListener('load', () =>
                {
                    this.fileLoadEnd(_resource, image)
                })

                image.addEventListener('error', () =>
                {
                    this.fileLoadEnd(_resource, image)
                })

                image.src = _resource.source
            }
        })


        // Draco
        const dracoLoader = new DRACOLoader()
        const templateUrl = TEMPLATE_URL
        dracoLoader.setDecoderPath(`${templateUrl}/static/draco/`)
        dracoLoader.setDecoderConfig({ type: 'js' })

        this.loaders.push({
            extensions: ['drc'],
            action: (_resource) =>
            {
                dracoLoader.load(_resource.source, (_data) =>
                {
                    this.fileLoadEnd(_resource, _data)

                    DRACOLoader.releaseDecoderModule()
                })
            }
        })

        // GLTF
        const gltfLoader = new GLTFLoader()
        gltfLoader.setDRACOLoader(dracoLoader)

// IMPORTANTISSIMO: settalo PRIMA di load/parse
        gltfLoader.setMeshoptDecoder(MeshoptDecoder);

        this.loaders.push({
            extensions: ['glb', 'gltf'],
            action: (_resource) =>
            {
                gltfLoader.load(_resource.source, (_data) =>
                {
                    this.fileLoadEnd(_resource, _data)
                })
            }
        })

        // FBX
        const fbxLoader = new FBXLoader()

        this.loaders.push({
            extensions: ['fbx'],
            action: (_resource) =>
            {
                fbxLoader.load(_resource.source, (_data) =>
                {
                    this.fileLoadEnd(_resource, _data)
                })
            }
        })

        // RGBE | HDR
        const rgbeLoader = new RGBELoader()

        this.loaders.push({
            extensions: ['hdr'],
            action: (_resource) =>
            {
                rgbeLoader.load(_resource.source, (_data) =>
                {
                    this.fileLoadEnd(_resource, _data)
                })
            }
        })


        const exrLoader = new EXRLoader()

        this.loaders.push({
            extensions: ['exr'],
            action: (_resource) => {
                exrLoader.load(_resource.source, (_data) => {
                    this.fileLoadEnd(_resource, _data)
                })
            }
        })


        // Audio
        this.loaders.push({
            extensions: ['mp3', 'wav', 'ogg'],
            action: (_resource) =>
            {
                const audioContext = this.getAudioContext()

                if(!audioContext)
                {
                    console.warn('AudioContext is not supported by this browser')
                    this.fileLoadEnd(_resource, null)
                    return
                }

                fetch(_resource.source)
                    .then((response) =>
                    {
                        if(!response.ok)
                        {
                            throw new Error(`Cannot load audio: ${_resource.source}`)
                        }

                        return response.arrayBuffer()
                    })
                    .then((arrayBuffer) => audioContext.decodeAudioData(arrayBuffer))
                    .then((audioBuffer) =>
                    {
                        this.fileLoadEnd(_resource, {
                            id: _resource.name,
                            type: 'audio',
                            source: _resource.source,
                            audioContext,
                            buffer: audioBuffer,
                            duration: audioBuffer.duration
                        })
                    })
                    .catch((error) =>
                    {
                        console.error(`Error loading audio: ${_resource.source}`, error)
                        this.fileLoadEnd(_resource, null)
                    })
            }
        })


        // Video (e.g. MP4, WebM)
        this.loaders.push({
            extensions: ['mp4', 'webm'],
            action: (_resource) =>
            {
                const video = document.createElement('video')
                video.src = _resource.source


                video.muted = true;
                video.playsInline = true;
                video.loop = true;
                video.setAttribute('playsinline', 'true');
                video.setAttribute('muted', 'true');
                video.setAttribute('autoplay', 'true');
                video.setAttribute('loop', 'true');


                // Ensure the video starts playing automatically
                video.addEventListener('loadeddata', () => {
                    video.play().catch(error => {
                        console.error('Video play failed:', error);
                    });
                    this.fileLoadEnd(_resource, video)
                })

                video.addEventListener('error', () => {
                    console.error(`Error loading video: ${_resource.source}`)
                    this.fileLoadEnd(_resource, video)
                })
            }
        })


        //json
        this.loaders.push({
            extensions: ['json'],
            action: (_resource) =>
            {

                if (_resource.type == 'font') {

                    const loader = new FontLoader();

                    loader.load(
                        _resource.source,
                        (font) => {
                            this.fileLoadEnd(_resource, font);
                        },
                        undefined,
                        (error) => {
                            console.error(`Errore nel caricamento del font: ${_resource.source}`, error);
                            this.fileLoadEnd(_resource, null);
                        }
                    );


                } else {
                    //console.log('json normale')
                }



            }
        })



    }

    getAudioContext()
    {
        if(this.audioContext)
        {
            return this.audioContext
        }

        const AudioContextClass = window.AudioContext || window.webkitAudioContext

        if(!AudioContextClass)
        {
            return null
        }

        this.audioContext = new AudioContextClass()

        return this.audioContext
    }

    /**
     * Load
     */
    load(_resources = [])
    {
        for(const _resource of _resources)
        {
            this.toLoad++
            const extensionMatch = _resource.source.match(/\.([a-z0-9]+)$/i)

            if(typeof extensionMatch[1] !== 'undefined')
            {
                const extension = extensionMatch[1]
                const loader = this.loaders.find((_loader) => _loader.extensions.find((_extension) => _extension === extension))

                if(loader)
                {
                    loader.action(_resource)
                }
                else
                {
                    console.warn(`Cannot found loader for ${_resource}`)
                }
            }
            else
            {
                console.warn(`Cannot found extension of ${_resource}`)
            }
        }
    }

    /**
     * File load end
     */
    fileLoadEnd(_resource, _data)
    {
        this.loaded++
        this.items[_resource.name] = _data

        this.trigger('fileEnd', [_resource, _data])

        if(this.loaded === this.toLoad)
        {
            this.trigger('end')
        }
    }
}

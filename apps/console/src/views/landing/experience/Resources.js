import * as THREE from 'three'
import EventEmitter from './Utils/EventEmitter.js'
import Loader from './Utils/Loader.js'

export default class Resources extends EventEmitter
{
    constructor(_assets)
    {
        super()

        // Items (will contain every resources)
        this.items = {}

        // Loader
        this.loader = new Loader()

        this.groups = {}
        this.groups.assets = [..._assets]
        this.groups.loaded = []
        this.groups.current = null
        this.loadNextGroup()

        // Loader file end event
        this.loader.on('fileEnd', (_resource, _data) =>
        {
            let data = _data

            // Convert to texture
            if(_resource.type === 'texture')
            {
                if(!(data instanceof THREE.Texture))
                {
                    data = new THREE.Texture(_data)
                }
                data.needsUpdate = true
            }

            // Convert to video texture
            if(_resource.type === 'video')
            {
                _data.play()
                data = new THREE.VideoTexture(_data)
                data.needsUpdate = true
            }

            this.items[_resource.name] = data


            if (_resource.rel) {
                data.userData.rel = _resource.rel
            }


            // Progress and event
            this.groups.current.loaded++
            this.trigger('progress', [this.groups.current, _resource, data])
        })

        // Loader all end event
        this.loader.on('end', () =>
        {
            this.groups.loaded.push(this.groups.current)

            // Trigger
            this.trigger('groupEnd', [this.groups.current])

            if(this.groups.assets.length > 0)
            {
                this.loadNextGroup()
            }
            else
            {
                this.trigger('end')
            }
        })
    }

    loadNextGroup()
    {
        this.groups.current = this.groups.assets.shift()
        this.groups.current.toLoad = this.groups.current.items.length
        this.groups.current.loaded = 0

        this.loader.load(this.groups.current.items)
    }

    destroy()
    {
        for(const _itemKey in this.items)
        {
            const item = this.items[_itemKey]
            if(item instanceof THREE.Texture)
            {
                item.dispose()
            }
        }
    }
}

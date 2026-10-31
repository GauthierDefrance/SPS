import { createApp } from 'vue'
import App from './App.vue'
import './index.css'
import { installVuetify } from './plugins/vuetify'
import { router } from './router'
import './App.css'

const app = createApp(App)
installVuetify(app)
app.use(router)
app.mount('#app')

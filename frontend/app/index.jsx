import React from 'react';
import {render} from 'react-dom';
import {BrowserRouter, Link, Route, Switch, Redirect} from 'react-router-dom';
import Raven from 'raven-js';
import StatsHistoryGraph from "./StatsHistoryGraph.jsx";
import RunsInProgress from "./RunsInProgress.jsx";
import CurrentStateStats from "./CurrentStateStats.jsx";

import { library } from '@fortawesome/fontawesome-svg-core'
import { faStroopwafel, faCheckCircle, faCheck, faTimes, faTimesCircle, faRoad, faSearch,faThList,faWrench, faLightbulb, faFolderPlus, faFolderMinus, faFolder, faBookReader, faRedoAlt, faHome } from '@fortawesome/free-solid-svg-icons'
import { faChevronCircleDown,faChevronCircleRight,faTrashAlt, faFilm, faVolumeUp,faImage, faFile, faClock, faRunning, faExclamationTriangle, faHdd, faBalanceScale, faSyncAlt, faIndustry, faListOl} from '@fortawesome/free-solid-svg-icons'
import { faCompressArrowsAlt, faBug, faExclamation, faUnlink } from '@fortawesome/free-solid-svg-icons'

library.add(faStroopwafel, faCheckCircle, faCheck, faTimes, faTimesCircle, faRoad,faSearch,faThList,faWrench, faLightbulb, faChevronCircleDown, faChevronCircleRight, faTrashAlt, faFolderPlus, faFolderMinus, faFolder);
library.add(faFilm, faVolumeUp, faImage, faFile, faClock, faRunning, faExclamationTriangle, faHdd, faBalanceScale, faSyncAlt, faBookReader, faBug, faCompressArrowsAlt, faIndustry, faRedoAlt, faHome, faListOl,);
library.add(faExclamation, faUnlink);

class App extends React.Component {
    render(){
        return <div>
            <h1>Media Census</h1>
            <RunsInProgress/>
            <CurrentStateStats/>
            <StatsHistoryGraph/>
        </div>
    }
}

render(<BrowserRouter root="/"><App/></BrowserRouter>, document.getElementById('app'));
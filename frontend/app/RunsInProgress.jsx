import React from 'react';
import PropTypes from 'prop-types';
import RefreshButton from "./common/RefreshButton.jsx";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx"
import moment from 'moment';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import axios from 'axios';
import TimestampDiffComponent from "./common/TimestampDiffComponent.jsx";

class RunsInProgress extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            runData:[]
        }
    }

    refresh(){
        this.setState({loading:true, lastError: null}, ()=>axios.get("/api/jobs/running").then(response=>{
            this.setState({loading: false, lastError: null, runData: response.data.entries})
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err});
        }))
    }

    componentWillMount() {
        this.refresh();
    }

    render() {
        return <div className="inprogress-container" style={{display: this.state.runData.length>0 ? "block" : "none"}}>
           <h3><FontAwesomeIcon icon="clock" style={{color: "red"}}/> Runs in progress</h3>
            <ul className="no-bullets">{
                this.state.runData.map(entry=><li key={entry.jobId}>Run started at {entry.scanStart} (
                    <TimestampDiffComponent endTime={entry.scanStart}/>)
                </li>)
            }</ul>
        </div>
    }
}

export default RunsInProgress;
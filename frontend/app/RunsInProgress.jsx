import React from 'react';
import PropTypes from 'prop-types';
import RefreshButton from "./common/RefreshButton.jsx";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx"
import moment from 'moment';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import axios from 'axios';
import TimestampDiffComponent from "./common/TimestampDiffComponent.jsx";
import ClickableIcon from "./common/ClickableIcon.jsx";
import LoadingThrobber from "./common/LoadingThrobber.jsx";
import Cookies from 'js-cookie';

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
        this.setState({loading:true, lastError: null}, ()=>axios.get("/api/jobs/running?limit=10").then(response=>{
            this.setState({loading: false, lastError: null, runData: response.data.entries})
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err});
        }))
    }

    componentWillMount() {
        this.refresh();
        window.setInterval(()=>this.refresh(), 10000);  //refresh every 10s
    }

    deletionClicked(entryId){
        const token = Cookies.get("csrftoken");
        this.setState({loading: true}, ()=>axios.request({
            url: "/api/job/" + entryId,
            method: "delete",
            headers: {
                "Csrf-Token": token,
                "Content-Type": "application/json"
            }
        }).then(response=>{
            this.setState({lastError: null}, ()=>window.setTimeout(()=>this.refresh(), 1000))
        }).catch(err=>{
            this.setState({loading: false, lastError: err});
        }))
    }

    render() {
        return <div className="inprogress-container" style={{display: this.state.runData.length>0 ? "block" : "none"}}>
           <h3><FontAwesomeIcon icon="clock" style={{color: "red"}}/> Runs in progress</h3>
            <ul className="no-bullets">{
                this.state.runData.map(entry=><li key={entry.jobId}>
                        {/*<ClickableIcon onClick={()=>this.deletionClicked(entry.jobId)} icon="times-circle"/>*/}
                        {entry.jobType} started <TimestampDiffComponent endTime={entry.scanStart}/><br/>{entry.itemsCounted} items so far
                </li>)
            }</ul>
            <LoadingThrobber show={this.state.loading} small={true}/>
            <ErrorViewComponent error={this.state.lastError}/>
        </div>
    }
}

export default RunsInProgress;
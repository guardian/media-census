import React from 'react';
import axios from 'axios';
import RefreshButton from "./common/RefreshButton.jsx";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import TimestampDiffComponent from "./common/TimestampDiffComponent.jsx";
import ClickableIcon from "./common/ClickableIcon.jsx";
import Cookies from "js-cookie";
import moment from 'moment';

class RunsAdmin extends React.Component {
    constructor(props) {
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            jobsList: []
        }
    }

    componentWillMount() {
        this.refresh();
    }

    refresh(){
        this.setState({loading: true},()=>axios.get("/api/jobs/all/forTimespan").then(response=>{
            this.setState({
                loading: false,
                lastError: null,
                jobsList: response.data.entries
            });
        }).catch(err=>{
            console.error(err);
            this.setState({
                loading: false,
                lastError:err
            })
        }))
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

    render(){
        return <div>
            <RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()}/>
            <ErrorViewComponent error={this.state.lastError}/>
            <table className="dashboardpanel">
                <thead>
                <tr className="dashboardheader">
                    <td>Delete record</td>
                    <td>Job ID</td>
                    <td>Type</td>
                    <td>Running time</td>
                    <td>Last error</td>
                    <td>Items counted</td>
                </tr>
                </thead>
                <tbody>
                {
                    this.state.jobsList.map(entry=><tr key={entry.jobId}>
                        <td><ClickableIcon onClick={()=>this.deletionClicked(entry.jobId)} icon="times-circle"/></td>
                        <td>{entry.jobId}</td>
                        <td>{entry.jobType}</td>
                        <td>{moment(entry.scanStart).format("ddd Do MMM")}, started {moment(entry.scanStart).format("HH:mm")}<br/>
                            {entry.scanFinish ? "Finished, ran for " : "Still running for "}
                            {entry.scanFinish ? <TimestampDiffComponent startTime={entry.scanStart} endTime={entry.scanFinish} prefix={false}/> : <TimestampDiffComponent startTime={entry.scanStart} prefix={false}/>}
                        </td>
                        <td>{entry.lastError ? entry.lastError : "none"}</td>
                        <td>{entry.itemsCounted}</td>
                    </tr>)
                }
                </tbody>
            </table>
        </div>
    }
}

export default RunsAdmin;
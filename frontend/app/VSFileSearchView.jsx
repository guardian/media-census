import React from "react";
import PropTypes from "prop-types";
import axios from "axios";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import BytesFormatter from "./common/BytesFormatter.jsx";

class VSFileSearchView extends React.Component {
    static propTypes = {
        startingTime: PropTypes.string,
        durationTime: PropTypes.number,
        visible: PropTypes.bool.isRequired
    };

    constructor(props) {
        super(props);

        this.state = {
            loading: false,
            filesList: [],
            totalCount: 0,
            lastError: null
        }
    }

    componentWillMount() {
        this.reloadData();
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        console.log("VSFileSearch update: ", prevProps, this.props);

        if ((prevProps.startingTime !== this.props.startingTime || this.props.durationTime !== prevProps.durationTime) && this.props.visible) {
            console.log("Reloading data");
            this.reloadData();
        }
    }

    reloadData() {
        let uriParams = {};
        if (this.props.startingTime && this.props.durationTime) {
            uriParams["start"] = this.props.startingTime;
            uriParams["duration"] = this.props.durationTime;
        }
        console.log(uriParams);
        if (Object.keys(uriParams).length > 0) {
            const uri = "/api/nearline/files?" + Object.keys(uriParams).map(k => k + "=" + uriParams[k]).join("&");

            this.setState({loading: true}, () => axios.get(uri).then(result => {
                this.setState({
                    loading: false,
                    lastError: null,
                    totalCount: result.data.entryCount,
                    filesList: result.data.entries
                })
            }).catch(err => {
                this.setState({
                    loading: false,
                    lastError: err
                })
            }))
        }
    }

    static pathSplitRegex = RegExp(/^(.*)\/([^/]+)$/);

    static splitFilePath(fullpath) {
        const matches = VSFileSearchView.pathSplitRegex.exec(fullpath);
        if (matches) {
            return [matches[1], matches[2]]
        } else {
            return ["",fullpath];
        }
    }

    render() {
        if (this.state.lastError) return <ErrorViewComponent error={this.state.lastError}/>;

        return <table className="dashboardpanel"
                      style={{width: "100%", display: this.props.visible ? "block" : "none"}}>
            <thead>
            <tr className="dashboardheader">
                <td style={{width:"50px"}}>Filename</td>
                <td style={{width:"150px"}}>Path</td>
                <td style={{width:"30px"}}>Size</td>
                <td>Timestamp</td>
                <td>State</td>
                <td>Storage</td>
                <td>Membership</td>
            </tr>
            </thead>
            <tbody>
            {this.state.filesList.map(entry => {
                const splitoutPath = VSFileSearchView.splitFilePath(entry.path);
                return <tr key={entry.vsid}>
                    <td style={{width:"50px"}}>{splitoutPath[1]}</td>
                    <td style={{width:"150px"}}>{splitoutPath[0]}</td>
                    <td style={{width:"30px"}}><BytesFormatter value={entry.size}/></td>
                    <td>{entry.timestamp}</td>
                    <td>{entry.state}</td>
                    <td>{entry.storage}</td>
                    <td>{entry.membership ? entry.membership.toString : <i>(none)</i>}</td>
                </tr>
            })}
            {
                this.state.filesList.length < this.state.totalCount ?
                    <tr><td colSpan={7} style={{textAlign:"center"}}><i>Results limited to {this.state.filesList.length}</i></td></tr> :
                    <tr><td colSpan={7} style={{textAlign:"center"}}><i>All results shown</i></td></tr>
            }
            </tbody>
        </table>
    }
}

export default VSFileSearchView;

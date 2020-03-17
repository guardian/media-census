import React from "react";
import PropTypes from "prop-types";
import axios from "axios";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import BytesFormatter from "./common/BytesFormatter.jsx";

class ProjectSearchView extends React.Component {
    static propTypes = {
        visible: PropTypes.bool.isRequired,
        projectStatus: PropTypes.string
    };

    constructor(props) {
        super(props);

        this.state = {
            loading: false,
            projectsList: [],
            totalCount: 0,
            lastError: null
        }
    }

    componentWillMount() {
        this.reloadData();
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        console.log("ProjectSearch update: ", prevProps, this.props);

        if ((prevProps.projectStatus !== this.props.projectStatus) && this.props.visible) {
            console.log("Reloading data");
            this.reloadData();
        }
    }

    reloadData() {
        let uriParams = {};
        if (this.props.projectStatus) {
            uriParams["status"] = this.props.projectStatus;
        }
        console.log(uriParams);
        if (Object.keys(uriParams).length > 0) {
            const uri = "/api/unclog/mediaStatus/" + this.props.projectStatus +"/projects";

            this.setState({loading: true}, () => axios.get(uri).then(result => {
                this.setState({
                    loading: false,
                    lastError: null,
                    totalCount: result.data.entry.length,
                    projectsList: result.data.entry
                })
            }).catch(err => {
                this.setState({
                    loading: false,
                    lastError: err
                })
            }))
        }
    }

    render() {
        if (this.state.lastError) return <ErrorViewComponent error={this.state.lastError}/>;

        return <table className="dashboardpanel"
                      style={{width: "100%", display: this.props.visible ? "block" : "none"}}>
            <thead>
            <tr className="dashboardheader">
                <td style={{width:"50px"}}>Project</td>
                <td style={{width:"120px"}}>File Count</td>
                <td style={{width:"200px"}}>File Size</td>
            </tr>
            </thead>
            <tbody>
            {this.state.projectsList.map(entry => {
                return <tr>
                    <td><a href={"https://pluto.gnm.int/project/"+entry.label} target="_blank">{entry.label}</a></td>
                    <td>{entry.count}</td>
                    <td>{entry.size}</td>

                </tr>
            })}
            {
                this.state.projectsList.length > 128 ?
                    <tr><td colSpan={7} style={{textAlign:"center"}}><i>More than 128 projects found</i></td></tr> :
                    <tr><td colSpan={7} style={{textAlign:"center"}}><i>All results shown</i></td></tr>
            }
            </tbody>
        </table>
    }
}

export default ProjectSearchView;

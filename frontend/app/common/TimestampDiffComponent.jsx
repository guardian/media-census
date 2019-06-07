import React from 'react';
import PropTypes from 'prop-types';
import moment from 'moment';

class TimestampDiffComponent extends React.Component {
    static propTypes = {
        startTime: PropTypes.string.isRequired,
        endTime: PropTypes.string,
        formatString: PropTypes.string,
        prefix: PropTypes.bool
    };

    choppedTimestamp(momentResult){
        return momentResult.replace(/^in\s*/,"");
    }

    render(){
        const formatToUse = this.props.formatString ? this.props.formatString : "";
        const startMoment = this.props.startTime ? moment(this.props.startTime) : moment();
        const endMoment = this.props.endTime ? moment(this.props.endTime) : moment();

        const out = endMoment.from(startMoment);

        const formatted = this.props.prefix ? out : this.choppedTimestamp(out.toString());
        return <span className="timestamp">{formatted}</span>
    }
}

export default TimestampDiffComponent;